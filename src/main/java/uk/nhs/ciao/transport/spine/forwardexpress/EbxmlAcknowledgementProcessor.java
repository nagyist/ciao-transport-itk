package uk.nhs.ciao.transport.spine.forwardexpress;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope;

/**
 * Processes incoming ebXml acknowledgements
 */
// TODO: Could this be included by default in the forward express component?
// Perhaps this could be handled as part of a route using some combination of
// convertBodyTo(), choose(), simple(), setFaultBody() and throwException()?
public class EbxmlAcknowledgementProcessor implements Processor {
	private static final Logger LOGGER = LoggerFactory.getLogger(EbxmlAcknowledgementProcessor.class);
	
	/**
	 * <p>
	 * From ebMS_v2_0.pdf:
	 * 
	 * 6.5.7 Failed Message Delivery
	 * If a message sent with an AckRequested element cannot be delivered, the MSH or process handling the
	 * message (as in the case of a routing intermediary) SHALL send a delivery failure notification to the From
	 * Party. The delivery failure notification message is an Error Message with <code>errorCode</code> of
	 * <code>DeliveryFailure</code> and a <code>severity</code> of:
	 * <ul>
	 * <li><code>Error</code> if the party who detected the problem could not transmit the message (e.g. the communications
	 * transport was not available)
	 * <li><code>Warning</code> if the message was transmitted, but an Acknowledgment Message was not received. This means
	 * the message probably was not delivered.
	 * 
	 * <p>
	 * From EIS11.6 (2.5.2):
	 * <p>
	 * Should an MHS receive an ebXML ErrorList with a highestSeverity of “Error” it MUST assume that the message in error can not
	 * be re-presented.  That is, the problem MUST be handled by the sender of the message in error.
	 * Should an MHS receive an ebXML ErrorList with a highestSeverity of “Warning” it MAY assume that the error is recoverable
	 * and that the message in error can be re-presented.
	 */
	@Override
	public void process(final Exchange exchange) throws Exception {
		final EbxmlEnvelope acknowledgment = exchange.getIn().getBody(EbxmlEnvelope.class);
		final String refToMessageId = acknowledgment.getMessageData().getRefToMessageId();
		
		if (acknowledgment.isErrorMessage()) {
			if (acknowledgment.getError().isWarning()) {
				onDeliveryFailureWarning(refToMessageId);
			} else {
				onDeliveryFailureError(refToMessageId, exchange);
			}
		} else {
			onSuccess(refToMessageId);
		}
	}
	
	/**
	 * Message has been successfully acknowledged
	 */
	private void onSuccess(final String refToMessageId) {
		LOGGER.info("ebXml ack received - refToMessageId: {}", refToMessageId);
	}

	/**
	 * A delivery failure has been reported for the message being acknowledged but
	 * further attempts to re-send the message may be possible
	 */
	private void onDeliveryFailureWarning(final String refToMessageId) throws Exception {
		final String message = "ebXml delivery failure (warning) received - refToMessageId: " +
				refToMessageId + " - will retry (if applicable)";
		LOGGER.info(message);
		
		// Exceptions are caught by camel and retried!
		throw new Exception(message);
	}
	
	/**
	 * A delivery failure has been reported for the message being acknowledged and
	 * no further attempts to re-send the message should be tried
	 */
	private void onDeliveryFailureError(final String refToMessageId, final Exchange exchange) {
		LOGGER.info("ebXml delivery failure (error) received - refToMessageId: {} - will not retry", refToMessageId);
		exchange.getIn().setFault(true); // Fault messages are not retried!
	}
}
