package uk.nhs.ciao.transport.spine.example;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;

import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.impl.SimpleRegistry;

import com.google.common.base.Strings;

/**
 * Simulates the receiver role in the 'End-Party Reliability' pattern
 * <p>
 * Additionally, puts the initial request message on a JMS queue to
 * trigger a sender to start the message exchange.
 */
public class EndPartyReliabilityTestApplication {
	private final CamelContext context;
	private final ProducerTemplate producerTemplate;
	
	private final UI ui;
	
	public EndPartyReliabilityTestApplication() throws Exception {
		final SimpleRegistry registry = new SimpleRegistry();
		
		this.context = new DefaultCamelContext(registry);
		context.setTracing(true);
		this.producerTemplate = new DefaultProducerTemplate(context);
		
		final ActiveMQComponent jmsComponent = new ActiveMQComponent();
		jmsComponent.setBrokerURL("tcp://localhost:61616");
		context.addComponent("jms", jmsComponent);
		
		context.addRoutes(new Routes());		
		context.start();
		producerTemplate.start();
		
		this.ui = new UI();
	}
	
	public class Routes extends RouteBuilder {
		@Override
		public void configure() throws Exception {
			from("jetty:http://0.0.0.0:8123/")
				.process(new RequestProcessor());
		}		
	}
	
	private class RequestProcessor implements Processor {
		@Override
		public void process(final Exchange exchange) throws Exception {
			final String id = exchange.getIn().getBody(String.class);
			exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "text/plain");			
			try {
				if (ui.acceptRequest(id)) {
					exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 202);
					exchange.getOut().setBody("OK");					
				} else {
					exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
					exchange.getOut().setBody("Rejected");
				}
			} catch (Exception e) {
				exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 501);
				exchange.getOut().setBody(e.getMessage());
			}
		}
	}
	
	private class UI extends JFrame {
		private static final long serialVersionUID = -8106767692924223535L;
		private final JTextField idText;
		private final JList list;
		private final DefaultListModel listModel;
		
		public UI() throws Exception {
			super("Test Responser");
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			
			listModel = new  DefaultListModel();
			list = new JList(listModel);
			
			final JPopupMenu popupMenu = new JPopupMenu();
			for (final String type: Arrays.asList("send-ack|ack", "send-ack|nack-retry", "send-ack|nack-failure")) {
				final JMenuItem item = new JMenuItem(type);
				item.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						postMessage(type);
					}
				});
				popupMenu.add(item);
			}
			popupMenu.addSeparator();
			
			final JMenuItem clearItem = new JMenuItem("Clear");
			clearItem.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					clearItem();
				}
			});			
			popupMenu.add(clearItem);
			
			list.addMouseListener(new MouseAdapter() {
			    public void mousePressed(final MouseEvent e)  {
			    	popup(e);
			    }
			    
			    public void mouseReleased(final MouseEvent e) {
			    	popup(e);
			    }

			    public void popup(final MouseEvent e) {
			        if (e.isPopupTrigger()) {
			            list.setSelectedIndex(list.locationToIndex(e.getPoint()));
			            if (list.getSelectedIndex() != -1) {
			            	popupMenu.show(list, e.getX(), e.getY());
			            }
			        }
			    }
			});
			
			idText = new JTextField();
			idText.addActionListener(new ActionListener() {				
				@Override
				public void actionPerformed(final ActionEvent e) {
					startRequest();
				}
			});
			
			final JPanel panel = new JPanel();
			panel.setLayout(new BorderLayout());
			panel.add(idText, BorderLayout.PAGE_START);
			panel.add(list, BorderLayout.CENTER);
			getContentPane().add(panel);
			
			pack();
			setSize(600, 600);
			setLocationRelativeTo(null);
			setVisible(true);
		}
		
		private void startRequest() {
			final String id = idText.getText();
			if (Strings.isNullOrEmpty(id)) {
				return;
			}
			
			idText.setText("");
			producerTemplate.asyncSendBody("jms:queue:documents", id);
		}
		
		public void addRequest(final String id) {
			listModel.addElement(id);
		}
		
		private void postMessage(final String type) {
			final String id = (String)list.getSelectedValue();
			if (id == null) {
				return;
			}
			list.clearSelection();
			
//			final Endpoint endpoint = context.getEndpoint("http4://localhost:8122/");
//			final Exchange exchange = endpoint.createExchange();
//			exchange.getIn().setHeader(Exchange.HTTP_METHOD, "POST");
//			exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/plain");
//			exchange.getIn().setBody(id + ":" + type);
//			producerTemplate.asyncSend(endpoint, exchange);
			
			
			//final String url = "jms:queue:itk-trunk/" + id + "/" + type.split("\\|")[0];
			
			final String url = "jms:topic:document-ebxml-acks";
			System.out.println("Sending message to: " + url);
			
			final Endpoint endpoint = context.getEndpoint(url);
			final Exchange exchange = endpoint.createExchange();
			exchange.getIn().setHeader("JMSCorrelationID", id);
			exchange.getIn().setBody(type.split("\\|")[1]);
			producerTemplate.send(endpoint, exchange);
		}
		
		private void clearItem() {
			final int index = list.getSelectedIndex();
			if (index == -1) {
				return;
			}
			list.clearSelection();
			
			listModel.remove(index);
		}
		
		public boolean acceptRequest(String id) throws Exception {
			final int option = JOptionPane.showConfirmDialog(this, "Accept request: " + id + "?",
					"Incoming HTTP request", JOptionPane.YES_NO_CANCEL_OPTION);
			if (option == JOptionPane.YES_OPTION) {
				System.out.println("yes");
				addRequest(id);
				return true;
			} else if (option == JOptionPane.NO_OPTION) {
				System.out.println("no");
				return false;
			} else {
				throw new Exception("Simulating a server error");
			}
		}
	}
	
	public static void main(final String[] args) throws Exception {
		new EndPartyReliabilityTestApplication();
	}
}
