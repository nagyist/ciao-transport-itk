package uk.nhs.ciao.transport.spine.route;

import java.util.Collections;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.spring.spi.TransactionErrorHandlerBuilder;
import org.apache.camel.util.toolbox.AggregationStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.SettableFuture;

import uk.nhs.ciao.transport.spine.forwardexpress.EbxmlAcknowledgementProcessor;
import uk.nhs.ciao.transport.spine.forwardexpress.ForwardExpressMessageExchange;

/**
 * Route to send outgoing multipart messages over spine and wait for async acknowledgement
 * <ul>
 * <li>Sends a multi-part trunk request message over the spine
 * <li>Blocks until an async ebXml ack is received off a configured JMS topic or a timeout occurs
 * <li>Marks message as success, retry or failure based on the ACK content
 */
public class MultipartMessageSenderRoute extends BaseRouteBuilder {
	private static final Logger LOGGER = LoggerFactory.getLogger(MultipartMessageSenderRoute.class);
	
	private String multipartMessageSenderUri;
	private String multipartMessageDestinationUri;
	private String ebxmlAckReceiverUri;
	
	public void setMultipartMessageSenderUri(final String multipartMessageSenderUri) {
		this.multipartMessageSenderUri = multipartMessageSenderUri;
	}
	
	public void setMultipartMessageDestinationUri(final String multipartMessageDestinationUri) {
		this.multipartMessageDestinationUri = multipartMessageDestinationUri;
	}
	
	public void setEbxmlAckReceiverUri(final String ebxmlAckReceiverUri) {
		this.ebxmlAckReceiverUri = ebxmlAckReceiverUri;
	}
	
	private String getForwardExpressHandlerUrl() {
		return internalDirectUri("forward-express-handler");
	}
	
	private String getForwardExpressAggregatorUrl() {
		return internalDirectUri("forward-express-aggregator");
	}
	
	@Override
	public void configure() throws Exception {
		configureMultipartMessageSender();
		configureForwardExpressSender();
		configureForwardAckReceiver();
		configureForwardExpressMessageAggregator();
	}
	
	/**
	 * Configures the message sender
	 * <p>
	 * The route is split via an internal direct route to allow easier
	 * configuration of the forward-express handler component
	 * 
	 * @see #configureForwardExpressHandler()
	 */
	private void configureMultipartMessageSender() throws Exception {
		from(multipartMessageSenderUri)
			.id("trunk-request-sender")
			.errorHandler(new TransactionErrorHandlerBuilder()
				.asyncDelayedRedelivery()
				.maximumRedeliveries(2)
				.backOffMultiplier(2)
				.redeliveryDelay(2000)
				.log(LOGGER)
				.logExhausted(true)
			)
			.transacted("PROPAGATION_NOT_SUPPORTED")
			
//			.doTry()
				.to(getForwardExpressHandlerUrl())
				.process(new EbxmlAcknowledgementProcessor())
//			.endDoTry()
//			.doCatch(HttpOperationFailedException.class)
//				.process(new HttpErrorHandler())
		.end();
	}

	private final Set<String> inprogressIds = Collections.newSetFromMap(Maps.<String, Boolean>newConcurrentMap());
	private final int aggregatorTimeout = 30000;
	
	/**
	 * Route to send an HTTP request/response to spine and wait
	 * for a related asynchronous ack message.
	 * <p>
	 * The thread sending a message to this route will <strong>block</strong> until:
	 * <ul>
	 * <li>the original request-response fails
	 * <li>or a timeout occurs while waiting for the ack
	 * <li>or the ack is received
	 */
	private void configureForwardExpressSender() throws Exception {
		from(getForwardExpressHandlerUrl())
			.routeId(getInternalRoutePrefix())
			.doTry()
				.setHeader(ForwardExpressMessageExchange.MESSAGE_TYPE, constant(ForwardExpressMessageExchange.REQUEST_MESSAGE))
				.setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
		
				// TODO: Work out how to maintain the original request body - but also examine the out response from the HTTP request-response call
				.multicast(AggregationStrategies.useOriginal())
					.bean(inprogressIds, "add(${header.CamelCorrelationId})")
					
					// TODO: Is a pipeline + checking of out message required here?
					.to(ExchangePattern.InOut, multipartMessageDestinationUri)
				.end()
				.setProperty(ForwardExpressMessageExchange.ACK_FUTURE, method(SettableFuture.class, "create"))
				.to(getForwardExpressAggregatorUrl())
				.process(new ForwardExpressMessageExchange.WaitForAck(aggregatorTimeout + 1000)) // timeout is slightly higher than the corresponding value in the aggregate
				.validate().simple("${body.isComplete()}")
				.transform().simple("${body.getAckBody()}")
				// If left as the final clause, transform appears to cause problems by
				// for the next processor in the chain by nulling the exchange on one of the messages
				// This *appears* to be a bug in how TransformProcessor uses ExchangeHelper.replaceMessage
				// when making a message copy - the out message is copied but the exchange is nulled on the non-replaced message
				// An addition processor (which does not need to use getIn().getExchange()) seems to resolve the problem
				.log(LoggingLevel.DEBUG, LOGGER, "Extracted acknowledgment for ${header.CamelCorrelationId}")
			.endDoTry()
			.doFinally()
				.process(new Processor() {
					@Override
					public void process(final Exchange exchange) throws Exception {
						// Mark the ID as no longer in-progress
						inprogressIds.remove(exchange.getIn().getHeader(Exchange.CORRELATION_ID));
					}
				})
			.end()
		.end();
	}
	
	private void configureForwardAckReceiver() throws Exception {
		final String messageIdHeader = "JMSMessageID";
		final String correlationIdHeader = "JMSCorrelationID";
		
		from(ebxmlAckReceiverUri)
			.routeId(getInternalRoutePrefix() + "-ack")
			// multiple threads may be running - only process each incoming ack once
			.idempotentConsumer(header(messageIdHeader), new MemoryIdempotentRepository())
			
			// only handle IDs currently active in this process
			.filter(method(inprogressIds, "contains(${header." + correlationIdHeader + "})"))
			.setHeader(Exchange.CORRELATION_ID, header(correlationIdHeader))
			.setHeader(ForwardExpressMessageExchange.MESSAGE_TYPE, constant(ForwardExpressMessageExchange.ACK_MESSAGE))
			.log("Incoming ebxml ack for ${header.CamelCorrelationId}")
			.to(getForwardExpressAggregatorUrl())
		.end();
	}
	
	/**
	 * Configures a direct route which aggregates incoming request messages with associated asynchronous ebXML acks
	 */
	private void configureForwardExpressMessageAggregator() throws Exception {
		// Correlate original requests with incoming acks
		// acks may be received with no corresponding open request (i.e.
		// the request originated in another process)
		from(getForwardExpressAggregatorUrl())
			.routeId(getInternalRoutePrefix() + "-aggregator")
			.aggregate(header(Exchange.CORRELATION_ID), new ForwardExpressMessageExchange.AggregationStrategy())
				.completionPredicate(method(ForwardExpressMessageExchange.class, "isComplete(${body})"))
				.completionTimeout(aggregatorTimeout)
			.log("Completed forward-express request-response aggregate: ${header.CamelCorrelationId}")
			.bean(ForwardExpressMessageExchange.class, "notifyCompletion(${body})")
		.end();
	}
}
