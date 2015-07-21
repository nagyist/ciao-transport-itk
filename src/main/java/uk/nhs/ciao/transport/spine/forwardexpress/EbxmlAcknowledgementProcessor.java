package uk.nhs.ciao.transport.spine.forwardexpress;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/**
 * Processes incoming ebXml acknowledgements
 */
// TODO: Could this be included by default in the forward express component?
public class EbxmlAcknowledgementProcessor implements Processor {
	private static final Logger LOGGER = LoggerFactory.getLogger(EbxmlAcknowledgementProcessor.class);
	
	@Override
	public void process(final Exchange exchange) throws Exception {
		final String id = exchange.getIn().getHeader(Exchange.CORRELATION_ID, String.class);
		final Document document = exchange.getIn().getBody(Document.class);
		
		final AcknowledgementType ackType = getAcknowledgementType(document);
		ackType.process(id, exchange);
	}
	
	public AcknowledgementType getAcknowledgementType(final Document document) {
		// TODO: check document content - for now return success ACK
		return AcknowledgementType.SUCCESS;
	}
	
	/**
	 * Represents the type of acknowledgement received
	 */
	private enum AcknowledgementType {
		/**
		 * Message has been successfully acknowledged
		 */
		SUCCESS {
			@Override
			public void process(final String id, final Exchange exchange) {
				LOGGER.info("ebXml ack received - id: " + id);
			}
		},
		
		/**
		 * An error has been reported for the message being acknowledged but
		 * further attempts to re-send the message may be possible
		 */
		RETRY {
			@Override
			public void process(final String id, final Exchange exchange) {
				LOGGER.info("ebXml nack (failure) received - id: " + id + " - will not retry");
				exchange.getIn().setFault(true); // Fault messages are not retried!
			}
		},
		
		/**
		 * A failure error has been reported for the message being acknowledged and
		 * no further attempts to re-send the message shoulld be tried
		 */
		FAILURE {
			@Override
			public void process(final String id, final Exchange exchange) throws Exception {
				final String message = "ebXml nack (retry) received - id: " + id + " - will retry";
				LOGGER.info(message);
				
				// Exceptions are caught by camel and retried!
				throw new Exception(message);
			}
		};
		
		public abstract void process(final String id, final Exchange exchange) throws Exception;
	}

// Notes about ebXml acknowledgements:
//	soap => http://schemas.xmlsoap.org/soap/envelope/
//	eb => http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd
//
//	For acknowledgement:
//
//		soap:Envelope
//		soap:Header
//		eb:Acknowledgment
//		eb:RefToMessageId
//
//	probably also includes
//
//		soap:Envelope
//		soap:Header
//		eb:MessageHeader
//		eb:RefToMessageId
//
//	For errors:
//
//		soap:Envelope
//		soap:Header
//		eb:ErrorList
//		eb:Error
//
//	with id identified in 
//
//		soap:Envelope
//		soap:Header
//		eb:MessageHeader
//		eb:RefToMessageId
}
