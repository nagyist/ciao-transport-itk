package uk.nhs.ciao.transport.spine.forwardexpress;

import static uk.nhs.ciao.transport.spine.forwardexpress.ForwardExpressSenderRoutes.*;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.model.ProcessorDefinition;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

public class ForwardExpressComponent extends DefaultComponent {
	private final Set<String> routeIds = Sets.newConcurrentHashSet();
	private final Processor processor;
	private volatile String name;
	private volatile String requestUri;	
	private volatile String toUri;
	private volatile int timeout = 30000;
	private volatile String replyUri;
	private volatile ProducerTemplate producerTemplate;
	
	public ForwardExpressComponent() {
		this(null);
	}
	
	public ForwardExpressComponent(final CamelContext context) {
		super(context);
		
		processor = new Processor() {
			@Override
			public void process(final Exchange exchange) throws Exception {
				producerTemplate.send(requestUri, exchange);
			}
		};
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(final String name) {
		this.name = name;
	}
	
	public String getToUri() {
		return toUri;
	}
	
	public void setToUri(final String toUri) {
		this.toUri = toUri;
	}
	
	public int getTimeout() {
		return timeout;
	}
	
	public void setTimeout(final int timeout) {
		this.timeout = timeout;
	}
	
	public String getReplyUri() {
		return replyUri;
	}
	
	public void setReplyUri(final String replyUri) {
		this.replyUri = replyUri;
	}
	

	@Override
	protected Endpoint createEndpoint(final String uri, final String remaining,
			final Map<String, Object> parameters) throws Exception {
		return new ProcessorEndpoint(uri, getCamelContext(), processor);
	}
	
	@Override
	public void start() throws Exception {
		super.start();
		
		Preconditions.checkNotNull(name, "name");
		Preconditions.checkNotNull(toUri, "toUri");
		Preconditions.checkNotNull(replyUri, "replyUri");
		
		if (producerTemplate == null) {
			producerTemplate = new DefaultProducerTemplate(getCamelContext());
		}
		producerTemplate.start();
		
		getCamelContext().addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				requestUri = "direct:" + name + "-request";
				final String aggregateUri = "direct:" + name + "-aggregate";
				
				routeIds.add(name + "-request");
				final ProcessorDefinition<?> def =
						from(requestUri).routeId(name + "-request");
				
				forwardExpressSender(getContext(), def)
					.to(toUri)
					.waitForResponse(aggregateUri, timeout,
						// Using a separate JMS component to ensure this route is not blocked
						// if all other consumer queue threads (on jms:) are waiting for ACK responses
						jmsForwardExpressAckReceiver(replyUri));
			}
		});
	}
	
	@Override
	public void stop() throws Exception {
		super.stop();
		
		if (producerTemplate != null) {
			producerTemplate.stop();
		}
		
		// Try to remove any created routes
		for (final Iterator<String> iterator = routeIds.iterator(); iterator.hasNext(); ) {
			final String routeId = iterator.next();
			getCamelContext().stopRoute(routeId);
			getCamelContext().removeRoute(routeId);
			iterator.remove();
		}
	}
}
