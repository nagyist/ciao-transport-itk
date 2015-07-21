package uk.nhs.ciao.transport.spine.forwardexpress;

import java.util.Collections;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.BuilderSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.SettableFuture;

public final class ForwardExpressSenderRoutes {
	private ForwardExpressSenderRoutes() {
		// Suppress default constructor
	}
	
	public static ForwardExpressSender forwardExpressSender(final ModelCamelContext context, final ProcessorDefinition<?> from) {
		return new ForwardExpressSender(context, from);
	}
	
	public static ForwardExpressAckReceiver forwardExpressAckReceiver(final String routeId, final String fromUri, final String messageIdHeader, final String correlationIdHeader) {
		return new ForwardExpressAckReceiver(routeId, fromUri, messageIdHeader, correlationIdHeader);
	}
	
	public static ForwardExpressMessageAggregator forwardExpressMessageAggregator(final String routeId, final String fromUri, final int timeout) {
		return new ForwardExpressMessageAggregator(routeId, fromUri, timeout);
	}
	
	public static class ForwardExpressSender {
		private BuilderSupport support;
		private ProcessorDefinition<?> from;
		private String toUri;
		
		public ForwardExpressSender(final ModelCamelContext context, final ProcessorDefinition<?> from) {
			this.support = new BuilderSupport(context) {
				// Not having FowardExpressSender extend to prevent additional methods leaking into the public scope
			};
			this.from = from;
		}
		
		public ForwardExpressSender to(final String toUri) {
			this.toUri = toUri;
			return this;
		}
		
		public ProcessorDefinition<?> waitForResponse(final ForwardExpressAckReceiver ackReceiver, final ForwardExpressMessageAggregator aggregator) throws Exception {
			final Set<String> inprogressIds = Collections.newSetFromMap(Maps.<String, Boolean>newConcurrentMap());
			
			support.getContext().addRoutes(ackReceiver.createRoute(inprogressIds, aggregator.fromUri));
			support.getContext().addRoutes(aggregator.createRoute());
			
			return from.doTry()
				.setHeader(ForwardExpressMessageExchange.MESSAGE_TYPE, support.constant(ForwardExpressMessageExchange.REQUEST_MESSAGE))
				.setHeader(Exchange.HTTP_METHOD, support.constant(org.apache.camel.component.http4.HttpMethods.POST))
				.multicast()
					.bean(inprogressIds, "add(${header.CamelCorrelationId})")
					.to(toUri)
				.end()
				.setProperty(ForwardExpressMessageExchange.ACK_FUTURE, support.method(SettableFuture.class, "create"))
				.to(aggregator.fromUri)
				.process(new ForwardExpressMessageExchange.WaitForAck(aggregator.timeout + 1000)) // timeout is slightly higher than the corresponding value in the aggregate
				.validate().simple("${body.isComplete()}")
				.transform().simple("${body.getAckBody()}")
			.endDoTry()
			.doFinally()
				.process(new Processor() {
					@Override
					public void process(final Exchange exchange) throws Exception {
						// Mark the ID as no longer in-progress
						inprogressIds.remove(exchange.getIn().getHeader(Exchange.CORRELATION_ID));
					}
				})
			;
		}
	}
	
	public static class ForwardExpressAckReceiver {
		private final String routeId;
		private final String fromUri;
		private final String messageIdHeader;
		private final String correlationIdHeader;
		
		public ForwardExpressAckReceiver(final String routeId, final String fromUri, final String messageIdHeader, final String correlationIdHeader) {
			this.routeId = routeId;
			this.fromUri = fromUri;
			this.messageIdHeader = messageIdHeader;
			this.correlationIdHeader = correlationIdHeader;
		}
		
		private RouteBuilder createRoute(final Set<String> inprogressIds, final String toUri) {
			return new RouteBuilder() {
				@Override
				public void configure() throws Exception {
					from(fromUri)
						.routeId(routeId)
						// multiple threads may be running - only process each incoming ack once
						.idempotentConsumer(header(messageIdHeader), new MemoryIdempotentRepository())
						
						// only handle IDs currently active in this process
						.filter(method(inprogressIds, "contains(${header." + correlationIdHeader + "})"))
						.setHeader(Exchange.CORRELATION_ID, header(correlationIdHeader))
						.setHeader(ForwardExpressMessageExchange.MESSAGE_TYPE, constant(ForwardExpressMessageExchange.ACK_MESSAGE))
						.log("Incoming ebxml ack for ${header.CamelCorrelationId}")
						.to(toUri)
						;
				}
			};
		}
	}
	
	/**
	 * Configures a direct route which aggregates incoming request messages with associated asynchronous ebXML acks
	 */
	public static class ForwardExpressMessageAggregator {
		private final String routeId;
		private final String fromUri;
		private final int timeout;
		
		public ForwardExpressMessageAggregator(final String routeId, final String fromUri, final int timeout) {
			this.routeId = routeId;
			this.fromUri = fromUri;
			this.timeout = timeout;
		}
		
		private RouteBuilder createRoute() {
			return new RouteBuilder() {
				@Override
				public void configure() throws Exception {
					// Correlate original requests with incoming acks
					// acks may be received with no corresponding open request (i.e.
					// the request originated in another process)
					from(fromUri)
						.routeId(routeId)
						.aggregate(header(Exchange.CORRELATION_ID), new ForwardExpressMessageExchange.AggregationStrategy())
							.completionPredicate(method(ForwardExpressMessageExchange.class, "isComplete(${body})"))
							.completionTimeout(timeout)
						.log("Completed forward-express request-response aggregate: ${header.CamelCorrelationId}")
						.bean(ForwardExpressMessageExchange.class, "notifyCompletion(${body})")
						;
				}
			};
		}
	}
}
