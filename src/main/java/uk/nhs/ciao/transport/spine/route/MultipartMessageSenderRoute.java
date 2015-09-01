package uk.nhs.ciao.transport.spine.route;

import static uk.nhs.ciao.transport.spine.forwardexpress.ForwardExpressSenderRoutes.*;

import org.apache.camel.Exchange;
import org.apache.camel.spring.spi.TransactionErrorHandlerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.nhs.ciao.transport.spine.forwardexpress.EbxmlAcknowledgementProcessor;

/**
 * Roue to send outgoing multipart messages over spine and wait for async acknowledgement
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
		configureForwardExpressHandler();
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
			
			// Store a copy of the outgoing request into the documents in-progress folder
//			.setHeader(Exchange.FILE_NAME, simple("${header." + IN_PROGRESS_FOLDER + "}/trunk-request"))
//			.to("file://.") // full location is determined by Exchange.FILE_NAME header
			
//			.doTry()
				.to(getForwardExpressHandlerUrl())
				.process(new EbxmlAcknowledgementProcessor())
//			.endDoTry()
//			.doCatch(HttpOperationFailedException.class)
//				.process(new HttpErrorHandler())
		.end();
	}
	
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
	private void configureForwardExpressHandler() throws Exception {
		final String requestRouteId = getInternalRoutePrefix();
		final String ackRouteId = getInternalRoutePrefix() + "-ack";
		final String aggregateRouteId = getInternalRoutePrefix() + "-aggregator";
		
		forwardExpressSender(getContext(),
			from(getForwardExpressHandlerUrl()).routeId(requestRouteId))
			.to(multipartMessageDestinationUri)
			.waitForResponse(
				forwardExpressAckReceiver(ackRouteId, ebxmlAckReceiverUri, "JMSMessageID", Exchange.CORRELATION_ID),
				forwardExpressMessageAggregator(aggregateRouteId, getForwardExpressAggregatorUrl(), 30000));
	}
}
