package uk.nhs.ciao.transport.dts.route;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import uk.nhs.ciao.camel.CamelUtils;
import uk.nhs.ciao.dts.AddressType;
import uk.nhs.ciao.dts.ControlFile;
import uk.nhs.ciao.dts.Event;
import uk.nhs.ciao.dts.MessageType;
import uk.nhs.ciao.dts.Status;
import uk.nhs.ciao.dts.StatusRecord;
import uk.nhs.ciao.transport.dts.address.DTSEndpointAddress;
import uk.nhs.ciao.transport.dts.sequence.IdGenerator;

/**
 * Unit tests for {@link DTSDistributionEnvelopeSenderRoute}
 */
public class DTSDistributionEnvelopeSenderRouteTest {
	private SimpleRegistry registry;
	private CamelContext context;
	private ProducerTemplate producerTemplate;
	private DTSDistributionEnvelopeSenderRoute route;
	private IdGenerator idGenerator;
	private MockEndpoint dtsMessageSender;
	private MockEndpoint notificationPayloadReceiver;
	private volatile String toDTS = "receiver";
	
	@Before
	public void setup() throws Exception {
		registry = new SimpleRegistry();
		context = new DefaultCamelContext(registry);
		
		producerTemplate = new DefaultProducerTemplate(context);
		setupRoute();
		
		context.start();
		producerTemplate.start();
	}
	
	@After
	public void tearDown() throws Exception {
		CamelUtils.stopQuietly(producerTemplate, context);
	}
	
	private void setupRoute() throws Exception {
		route = new DTSDistributionEnvelopeSenderRoute();
		route.setDistributionEnvelopeSenderUri("stub:seda:distribution-envelope-sender");
		route.setDTSMessageSenderUri("mock:dts-message-sender");
		route.setDTSTemporaryFolder("../sender-temp/");		
		route.setDTSMessageSendNotificationReceiverUri("stub:send:send-notification-sender");
		route.setDistributionEnvelopeResponseUri("mock:notification-payload-receiver");
		
		registry.put("dtsSentIdempotentRepository", new MemoryIdempotentRepository());
		route.setIdempotentRepositoryId("dtsSentIdempotentRepository");
		
		registry.put("dtsSentInProgressRepository", new MemoryIdempotentRepository());
		route.setInProgressRepositoryId("dtsSentInProgressRepository");
		
		idGenerator = Mockito.mock(IdGenerator.class);
		route.setIdGenerator(idGenerator);
		
		route.setEndpointAddressEnricherUri("direct:endpoint-address-enricher");
		
		final ControlFile controlFile = new ControlFile();
		controlFile.setFromDTS("sender");
		controlFile.setWorkflowId("sender-workflow");
		route.setPrototypeControlFile(controlFile);
		
		context.addRoutes(route);
		
		// Always resolve to the specified DTS address
		context.addRoutes(new RouteBuilder() {			
			@Override
			public void configure() throws Exception {
				from("direct:endpoint-address-enricher")
					.process(new Processor() {
						@Override
						public void process(final Exchange exchange) throws Exception {
							final DTSEndpointAddress address = exchange.getIn().getBody(DTSEndpointAddress.class);
							address.setDtsMailbox(toDTS);
							exchange.getIn().setBody(address);
						}
					})
				.end();
			}
		});
		
		dtsMessageSender = MockEndpoint.resolve(context, "mock:dts-message-sender");
		dtsMessageSender.setSynchronous(true);
		notificationPayloadReceiver = MockEndpoint.resolve(context, "mock:notification-payload-receiver");
		notificationPayloadReceiver.setSynchronous(true);
	}
	
	@Test
	public void testSuccessSendNotificationIsPublished() throws Exception {
		final String id = "1234";
		final ControlFile controlFile = generateControlFile(id);
		
		final StatusRecord statusRecord = new StatusRecord();
		statusRecord.setEvent(Event.TRANSFER);
		statusRecord.setStatus(Status.SUCCESS);
		statusRecord.applyDefaults();
		controlFile.setStatusRecord(statusRecord);
		
		// expectations
		notificationPayloadReceiver.expectedMessageCount(1);
		
		// publish the notification
		sendNotification(controlFile);
		
		// verify result
		notificationPayloadReceiver.assertIsSatisfied(50);
		
		final Message message = notificationPayloadReceiver.getExchanges().get(0).getIn();
		Assert.assertEquals("sent", message.getHeader("ciao.messageSendNotification"));
		Assert.assertEquals(id, message.getHeader(Exchange.CORRELATION_ID));
	}
	
	@Test
	public void testFailureSendNotificationIsPublished() throws Exception {
		final String id = "1234";
		final ControlFile controlFile = generateControlFile(id);
		
		final StatusRecord statusRecord = new StatusRecord();
		statusRecord.setEvent(Event.TRANSFER);
		statusRecord.setStatus(Status.ERROR);
		statusRecord.applyDefaults();
		controlFile.setStatusRecord(statusRecord);
		
		// expectations
		notificationPayloadReceiver.expectedMessageCount(1);
		
		// publish the notification
		sendNotification(controlFile);
		
		// verify result
		notificationPayloadReceiver.assertIsSatisfied(50);
		
		final Message message = notificationPayloadReceiver.getExchanges().get(0).getIn();
		Assert.assertEquals("send-failed", message.getHeader("ciao.messageSendNotification"));
		Assert.assertEquals(id, message.getHeader(Exchange.CORRELATION_ID));
	}
	
	private Exchange sendNotification(final ControlFile controlFile) {
		final Exchange exchange = new DefaultExchange(context);
		exchange.setPattern(ExchangePattern.InOut);
		
		exchange.getIn().setHeader(Exchange.FILE_NAME, controlFile.getLocalId() + ".ctl");
		exchange.getIn().setBody(controlFile, String.class);
		
		return producerTemplate.send("stub:send:send-notification-sender", exchange);
	}
	
	private ControlFile generateControlFile(final String id) {
		final ControlFile controlFile = new ControlFile();
		controlFile.setAddressType(AddressType.DTS);
		controlFile.setFromDTS("sender");
		controlFile.setToDTS(toDTS);
		controlFile.setWorkflowId("sender-workflow");
		controlFile.setLocalId(id);
		controlFile.setMessageType(MessageType.Data);
		controlFile.applyDefaults();
		return controlFile;
	}
}
