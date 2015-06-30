package uk.nhs.ciao.transport.spine.forwardexpress;

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
 * for the 'Forward Express' / 'End-Party Reliability' pattern.
 */
public class ForwardExpressMessageExchange {
	public static final String ACK_FUTURE = "ciao.future.ack";
	public static final String MESSAGE_TYPE = "ciao.message.type";
	public static final String REQUEST_MESSAGE = "forward-express-request";
	public static final String ACK_MESSAGE = "forward-express-ack";
	
	private final String correlationId;
	private Exchange request;
	private Exchange ack;
	
	public ForwardExpressMessageExchange(final String correlationId) {
		this.correlationId = correlationId;
	}
	
	public boolean isComplete() {
		return request != null && ack != null;
	}
	
	public static boolean isComplete(final ForwardExpressMessageExchange messageExchange) {
		return messageExchange == null ? false : messageExchange.isComplete();
	}
	
	public static void notifyCompletion(final ForwardExpressMessageExchange messageExchange) {
		if (messageExchange == null || messageExchange.request == null) {
			return;
		}
		
		@SuppressWarnings("unchecked")
		final SettableFuture<ForwardExpressMessageExchange> future = messageExchange.request.getProperty(ACK_FUTURE, SettableFuture.class);
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
		final String messageType = exchange.getIn().getHeader(MESSAGE_TYPE, String.class);
		if (request == null && REQUEST_MESSAGE.equals(messageType)) {
			request = exchange;
		} else if (ACK_MESSAGE.equals(messageType)) {
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
			final Future<ForwardExpressMessageExchange> future = exchange.getProperty(ACK_FUTURE, Future.class);
			final ForwardExpressMessageExchange messageExchange = future.get(timeoutInMillis, TimeUnit.MILLISECONDS);
			exchange.getOut().copyFrom(exchange.getIn());
			exchange.getOut().setBody(messageExchange);
		}
	}
	
	/**
	 * Stores incoming exchanges as a {@link ForwardExpressMessageExchange} instance on the body of the aggregate exchange
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
				result.getIn().setBody(new ForwardExpressMessageExchange(correlationId));
			} else {
				result = oldExchange;
			}
			
			// Aggregate the incoming exchange
			result.getIn().getBody(ForwardExpressMessageExchange.class).aggregate(newExchange);
			
			return result;
		}
	}
}