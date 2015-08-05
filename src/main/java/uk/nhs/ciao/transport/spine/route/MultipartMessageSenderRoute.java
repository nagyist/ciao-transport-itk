package uk.nhs.ciao.transport.spine.route;

import static uk.nhs.ciao.transport.spine.forwardexpress.ForwardExpressSenderRoutes.forwardExpressAckReceiver;
import static uk.nhs.ciao.transport.spine.forwardexpress.ForwardExpressSenderRoutes.forwardExpressMessageAggregator;
import static uk.nhs.ciao.transport.spine.forwardexpress.ForwardExpressSenderRoutes.forwardExpressSender;

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
public class MultipartMessageSenderRoute extends BaseRouteBuilder {
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
	
	@Override
	public void configure() throws Exception {
		final String requestRouteId = getInternalRoutePrefix();
		final String ackRouteId = getInternalRoutePrefix() + "-ack";
		final String aggregateRouteId = getInternalRoutePrefix() + "-aggregate";
		
		forwardExpressSender(getContext(),
			from(multipartMessageSenderUri).routeId(requestRouteId))
			.to(multipartMessageDestinationUri)
			.waitForResponse(
				forwardExpressAckReceiver(ackRouteId, ebxmlAckReceiverUri, "JMSMessageID", "JMSCorrelationId"),
				forwardExpressMessageAggregator(aggregateRouteId, "direct:" + aggregateRouteId, 30000));
	}
}
