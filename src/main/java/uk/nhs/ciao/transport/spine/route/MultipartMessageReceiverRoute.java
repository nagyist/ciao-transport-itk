package uk.nhs.ciao.transport.spine.route;

import static uk.nhs.ciao.transport.spine.route.EbxmlManifestVerifier.MANIFEST_PROPERTY;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.nhs.ciao.transport.spine.multipart.MultipartBody;

/**
 * Routes to handle incoming multipart messages from spine.
 * <p>
 * The incoming manifest is verified, payload message published, and async ebxml
 * acknowledgement is sent. The processing of the underlying payload is the 
 * responsibility of another route. The ebxml ack is sent after the payload has
 * been extracted and stored for later processing.
 */
public class MultipartMessageReceiverRoute extends RouteBuilder {
	private static final Logger LOGGER = LoggerFactory.getLogger(MultipartMessageReceiverRoute.class);
	
	// TODO: Make in/out route URLs configurable
	@Override
	public void configure() throws Exception {		
		configureMultipartReceiver();
		configurePayloadPublisher();
		configureEbxmlAckSender();
	}
	
	/**
	 * Route to receive a multi-part message, process the manifest and send the sync response
	 * (no content, or SOAPFault)
	 * <p>
	 * The payload is extracted and sent for publishing via a separate route
	 */
	private void configureMultipartReceiver() {
		from("direct:multipart-receiver")
			// Start publishing the payload after the initial HTTP response has been sent
			.onCompletion().onCompleteOnly()
				.setExchangePattern(ExchangePattern.InOnly)
				.setBody(property("multipart-payload"))
				.to("seda:multipart-payload-publisher")
			.end()
			
			.convertBodyTo(MultipartBody.class)
			.log(LoggingLevel.DEBUG, LOGGER, "Converted to multipart body: ${body}")
			.process(new EbxmlManifestVerifier())
	
			// Store the payload in a property so it can be published after the main response is sent
			.setProperty("multipart-payload").spel("#{body.getParts().get(2).getBody()}")
			
			// HTTP sync response
			.setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
			.setBody(constant(""))
		.end();
	}
	
	/**
	 * Route to publish the payload and send the corresponding async ebxml ack
	 * <p>
	 * 'Publishing' the payload in this context means sending the payload to resilient route 
	 * (e.g. JMS queue / data store) for later processing. The nature of the processing is
	 * determined by the type / content of the payload.
	 */
	private void configurePayloadPublisher() {
		from("seda:multipart-payload-publisher")
			// On failure - send ebxml delivery failure notification
			.onCompletion().onFailureOnly()
				.setBody().property(MANIFEST_PROPERTY)
				.setBody().spel("#{body.generateDeliveryFailureNotification('Unable to deliver payload')}")
				.to("seda:ebxml-ack-sender")
			.end()
	
			// Publish ITK message for processing - but only if not successfully processed already
			.idempotentConsumer(simple("${property." + MANIFEST_PROPERTY + ".messageData.messageId}"),
					new MemoryIdempotentRepository())
			.eager(false)
			.removeOnFailure(true)
			.skipDuplicate(false)
				// only publish if not handled already
				.filter(property(Exchange.DUPLICATE_MESSAGE).isNull())
					.to("mock:multipart-payload-receiver")
				.end()
	
				// always send ebxml ack (i.e. if previously acked or if publishing was successful)
				.setBody().property(MANIFEST_PROPERTY)
				.setBody(simple("${body.generateAcknowledgment()}"))
				.to("seda:ebxml-ack-sender")
			.end()
		.end();
	}
	
	/**
	 * Route to send outgoing ebxml acknowledgement and delivery fault async responses
	 */
	// TODO: does outgoing ack response need retry logic / error hander?
	private void configureEbxmlAckSender() {
		from("seda:ebxml-ack-sender")
			.convertBodyTo(String.class)
			.to("mock:multipart-ack-sender");
	}
}