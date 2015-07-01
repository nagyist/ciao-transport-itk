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
	private volatile String ackCorrelationIdHeader = "JMSCorrelationId";
	private volatile String ackMessageIdHeader = "JMSMessageID";
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
	
	public String getAckMessageIdHeader() {
		return ackMessageIdHeader;
	}
	
	public void setAckMessageIdHeader(final String ackMessageIdHeader) {
		this.ackMessageIdHeader = ackMessageIdHeader;
	}
	
	public String getAckCorrelationIdHeader() {
		return ackCorrelationIdHeader;
	}
	
	public void setAckCorrelationIdHeader(final String ackCorrelationIdHeader) {
		this.ackCorrelationIdHeader = ackCorrelationIdHeader;
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
		Preconditions.checkNotNull(ackMessageIdHeader, "ackMessageIdHeader");
		Preconditions.checkNotNull(ackCorrelationIdHeader, "ackCorrelationIdHeader");
		
		if (producerTemplate == null) {
			producerTemplate = new DefaultProducerTemplate(getCamelContext());
		}
		producerTemplate.start();
		
		getCamelContext().addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				final String requestRouteId = name + "-request";
				final String ackRouteId = name + "-ack";
				final String aggregateRouteId = name + "-aggregate";
				
				routeIds.add(requestRouteId);
				routeIds.add(ackRouteId);
				routeIds.add(aggregateRouteId);
				
				requestUri = "direct:" + requestRouteId;
				
				forwardExpressSender(getContext(),
					from(requestUri).routeId(requestRouteId))
					.to(toUri)
					.waitForResponse(
						forwardExpressAckReceiver(ackRouteId, replyUri, ackMessageIdHeader, ackCorrelationIdHeader),
						forwardExpressMessageAggregator(aggregateRouteId, "direct:" + aggregateRouteId, timeout));
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
