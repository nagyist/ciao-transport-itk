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
	
	public static ForwardExpressAckReceiver forwardExpressAckReceiver(final String fromUri, final String messageIdHeader, final String correlationIdHeader) {
		return new ForwardExpressAckReceiver(fromUri, messageIdHeader, correlationIdHeader);
	}
	
	public static ForwardExpressAckReceiver jmsForwardExpressAckReceiver(final String fromUri) {
		return forwardExpressAckReceiver(fromUri, "JMSMessageID", "JMSCorrelationId");
	}
	
	public static class ForwardExpressSender {
		private BuilderSupport support;
		private ProcessorDefinition<?> from;
		private String toUri;
		
		public ForwardExpressSender(final ModelCamelContext context, final ProcessorDefinition<?> from) {
			this.support = new BuilderSupport(context) {};
			this.from = from;
		}
		
		public ForwardExpressSender to(final String toUri) {
			this.toUri = toUri;
			return this;
		}
		
		public ProcessorDefinition<?> waitForResponse(final String aggregateUri, final int timeout, final ForwardExpressAckReceiver ackReceiver) throws Exception {
			final Set<String> inprogressIds = Collections.newSetFromMap(Maps.<String, Boolean>newConcurrentMap());
			
			support.getContext().addRoutes(ackReceiver.createRoute(inprogressIds, aggregateUri));
			support.getContext().addRoutes(new ForwardExpressMessageAggregator(aggregateUri, timeout));
			
			return from.doTry()
				.setHeader(ForwardExpressMessageExchange.MESSAGE_TYPE, support.constant(ForwardExpressMessageExchange.REQUEST_MESSAGE))
				.multicast()
					.bean(inprogressIds, "add(${header.CamelCorrelationId})")
					.to(toUri)
				.end()
				.setProperty(ForwardExpressMessageExchange.ACK_FUTURE, support.method(SettableFuture.class, "create"))
				.to(aggregateUri)
				.process(new ForwardExpressMessageExchange.WaitForAck(timeout + 1000)) // timeout is slightly higher than the corresponding value in the aggregate
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
		private final String fromUri;
		private final String messageIdHeader;
		private final String correlationIdHeader;
		
		public ForwardExpressAckReceiver(final String fromUri, final String messageIdHeader, final String correlationIdHeader) {
			this.fromUri = fromUri;
			this.messageIdHeader = messageIdHeader;
			this.correlationIdHeader = correlationIdHeader;
		}
		
		private RouteBuilder createRoute(final Set<String> inprogressIds, final String toUri) {
			return new RouteBuilder() {
				@Override
				public void configure() throws Exception {
					from(fromUri)
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
	private static class ForwardExpressMessageAggregator extends RouteBuilder {
		private final String fromUri;
		private final int timeout;
		
		public ForwardExpressMessageAggregator(final String fromUri, final int timeout) {
			this.fromUri = fromUri;
			this.timeout = timeout;
		}
		
		@Override
		public void configure() throws Exception {
			// Correlate original requests with incoming acks
			// acks may be received with no corresponding open request (i.e.
			// the request originated in another process)
			from(fromUri)
				.aggregate(header(Exchange.CORRELATION_ID), new ForwardExpressMessageExchange.AggregationStrategy())
					.completionPredicate(method(ForwardExpressMessageExchange.class, "isComplete(${body})"))
					.completionTimeout(timeout)
				.log("Completed forward-express request-response aggregate: ${header.CamelCorrelationId}")
				.bean(ForwardExpressMessageExchange.class, "notifyCompletion(${body})")
				;
		}
	}
}
