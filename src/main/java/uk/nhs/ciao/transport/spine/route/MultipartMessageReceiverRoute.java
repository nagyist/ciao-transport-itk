package uk.nhs.ciao.transport.spine.route;

import static uk.nhs.ciao.transport.spine.route.EbxmlManifestVerifier.MANIFEST_PROPERTY;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.spi.IdempotentRepository;
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
	private static final String PAYLOAD_PROPERTY = "multipart-payload";
	
	// TODO: Make in/out route URLs configurable
	
	/**
	 * URI where incoming multipart messages are received from
	 * <p>
	 * input only
	 */
	private final String multipartReceiverUri = "direct:multipart-receiver";
	
	/**
	 * URI of internal route to publish outgoing payloads and to create the
	 * associated async ebxml responses
	 * <p>
	 * input and output (internal route)
	 */
	private final String payloadPublisherUri = "seda:multipart-payload-publisher";
	
	/**
	 * URI where outgoing payload messages are sent to
	 * <p>
	 * output only
	 */
	private final String payloadDestinationUri = "mock:multipart-payloads";
	
	/**
	 * URI of internal route to send outgoing ebxml responses
	 * <p>
	 * input and output (internal route)
	 */
	private final String ebxmlResponseSenderUri = "seda:ebxml-response-sender";	
	
	/**
	 * URI where outgoing ebxml response messages are sent to
	 * <p>
	 * output only
	 */
	private final String ebxmlResponseDestinationUri = "mock:ebxml-responses";
	
	
	// TODO: Make IdempotentRepository configurable
	private final IdempotentRepository<?> idempotentRepository = new MemoryIdempotentRepository();
	
	@Override
	public void configure() throws Exception {		
		configureMultipartReceiver();
		configurePayloadPublisher();
		configureEbxmlResponseSender();
	}
	
	/**
	 * Route to receive a multi-part message, process the manifest and send the sync response
	 * (no content, or SOAPFault)
	 * <p>
	 * The payload is extracted and sent for publishing via a separate route
	 */
	private void configureMultipartReceiver() {
		from(multipartReceiverUri)
			// Start publishing the payload after the initial HTTP response has been sent
			.onCompletion().onCompleteOnly()
				.setExchangePattern(ExchangePattern.InOnly)
				.setBody(property(PAYLOAD_PROPERTY))
				.to(payloadPublisherUri)
			.end()
			
			.convertBodyTo(MultipartBody.class)
			.log(LoggingLevel.DEBUG, LOGGER, "Converted to multipart body: ${body}")
			.process(new EbxmlManifestVerifier())
	
			// Store the payload in a property so it can be published after the main response is sent
			.setProperty(PAYLOAD_PROPERTY).spel("#{body.parts[2].body}")
			
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
		from(payloadPublisherUri)
			// On failure - send ebxml delivery failure notification
			.onCompletion().onFailureOnly()
				.setBody().property(MANIFEST_PROPERTY)
				.setBody().spel("#{body.generateDeliveryFailureNotification('Unable to deliver payload')}")
				.to(ebxmlResponseSenderUri)
			.end()
	
			// Publish payload message for processing - but only if not successfully processed already
			.idempotentConsumer(simple("${property." + MANIFEST_PROPERTY + ".messageData.messageId}"),
					idempotentRepository)
			.eager(false)
			.removeOnFailure(true)
			.skipDuplicate(false)
				// only publish if not handled already
				.filter(property(Exchange.DUPLICATE_MESSAGE).isNull())
					.to(payloadDestinationUri)
				.end()
	
				// always send ebxml ack (i.e. if previously acked or if publishing was successful)
				.setBody().property(MANIFEST_PROPERTY)
				.setBody(simple("${body.generateAcknowledgment()}"))
				.to(ebxmlResponseSenderUri)
			.end()
		.end();
	}
	
	/**
	 * Route to send outgoing ebxml acknowledgement and delivery fault async responses
	 */
	// TODO: does outgoing ack response need retry logic / error hander?
	private void configureEbxmlResponseSender() {
		from(ebxmlResponseSenderUri)
			.convertBodyTo(String.class)
			.to(ebxmlResponseDestinationUri);
	}
}