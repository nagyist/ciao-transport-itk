package uk.nhs.ciao.transport.spine.example;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultExchange;

import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.SettableFuture;

/**
 * Represents the aggregate of an initial synchronous request-response
 * and a later asynchronous ACK response which form the message exchange
 * for the 'End-Party Reliability' pattern.
 */
public class EndPartyReliabilityMessageExchange {
	public static final String CIAO_FUTURE = "ciao.future";
	
	private final String correlationId;
	private Exchange request;
	private Exchange ack;
	
	public EndPartyReliabilityMessageExchange(final String correlationId) {
		this.correlationId = correlationId;
	}
	
	public boolean isComplete() {
		return request != null && ack != null;
	}
	
	public static boolean isComplete(final EndPartyReliabilityMessageExchange messageExchange) {
		return messageExchange == null ? false : messageExchange.isComplete();
	}
	
	public static void notifyCompletion(final EndPartyReliabilityMessageExchange messageExchange) {
		if (messageExchange == null || messageExchange.request == null) {
			return;
		}
		
		@SuppressWarnings("unchecked")
		final SettableFuture<EndPartyReliabilityMessageExchange> future = messageExchange.request.getProperty(CIAO_FUTURE, SettableFuture.class);
		if (future != null) {
			future.set(messageExchange);
		}
	}
	
	public Exchange getAck() {
		return ack;
	}
	
	public Object getAckBody() {
		return ack == null ? null : ack.getIn().getBody();
	}
	
	public void aggregate(final Exchange exchange) {
		final String messageType = exchange.getIn().getHeader("ciao.message.type", String.class);
		if (request == null && "trunk-request".equals(messageType)) {
			request = exchange;
		} else if ("trunk-ack".equals(messageType)) {
			ack = exchange;
		}
	}
	
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("correlationId", correlationId)
				.add("request", request)
				.add("ack", ack)
				.toString();
	}
	
	/**
	 * Processor to block processing of the current thread until
	 * an ack has been received for the message exchange or a timeout
	 * occurs
	 */
	public static class WaitForAck implements Processor {
		private final int timeoutInMillis;
		
		public WaitForAck(final int timeoutInMillis) {
			this.timeoutInMillis = timeoutInMillis;
		}
		
		@Override
		public void process(final Exchange exchange) throws Exception {
			@SuppressWarnings("unchecked")
			final Future<EndPartyReliabilityMessageExchange> future = exchange.getProperty(CIAO_FUTURE, Future.class);
			final EndPartyReliabilityMessageExchange messageExchange = future.get(timeoutInMillis, TimeUnit.MILLISECONDS);
			exchange.getOut().copyFrom(exchange.getIn());
			exchange.getOut().setBody(messageExchange);
		}
	}
	
	/**
	 * Stores incoming exchanges as a {@link EndPartyReliabilityMessageExchange} instance on the body of the aggregate exchange
	 */
	public static class AggregationStrategy implements org.apache.camel.processor.aggregate.AggregationStrategy {
		@Override
		public Exchange aggregate(final Exchange oldExchange, final Exchange newExchange) {
			final String correlationId = newExchange.getIn().getHeader(Exchange.CORRELATION_ID, String.class);
			
			final Exchange result;
			if (oldExchange == null) {
				result = new DefaultExchange(newExchange);
				if (correlationId != null) {
					result.getIn().setHeader(Exchange.CORRELATION_ID, correlationId);
				}
				result.getIn().setBody(new EndPartyReliabilityMessageExchange(correlationId));
			} else {
				result = oldExchange;
			}
			
			// Aggregate the incoming exchange
			result.getIn().getBody(EndPartyReliabilityMessageExchange.class).aggregate(newExchange);
			
			return result;
		}
	}
}