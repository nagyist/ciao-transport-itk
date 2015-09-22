package uk.nhs.ciao.transport.spine.route;

import static uk.nhs.ciao.logging.CiaoCamelLogMessage.camelLogMsg;
import static uk.nhs.ciao.transport.spine.route.EbxmlManifestVerifier.MANIFEST_PROPERTY;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.spi.IdempotentRepository;

import uk.nhs.ciao.camel.BaseRouteBuilder;
import uk.nhs.ciao.logging.CiaoCamelLogger;
import uk.nhs.ciao.transport.spine.multipart.MultipartBody;

/**
 * Routes to handle incoming multipart messages from spine.
 * <p>
 * The incoming manifest is verified, payload message published, and async ebxml
 * acknowledgement is sent. The processing of the underlying payload is the 
 * responsibility of another route. The ebxml ack is sent after the payload has
 * been extracted and stored for later processing.
 */
public class MultipartMessageReceiverRoute extends BaseRouteBuilder {
	private static final CiaoCamelLogger LOGGER = CiaoCamelLogger.getLogger(MultipartMessageReceiverRoute.class);
	private static final String PAYLOAD_PROPERTY = "multipart-payload";
	
	private String multipartReceiverUri;
	private String payloadDestinationUri;
	private String ebxmlResponseDestinationUri;
	private IdempotentRepository<?> idempotentRepository;
	
	/**
	 * URI where incoming multipart messages are received from
	 * <p>
	 * input only
	 */
	public void setMultipartReceiverUri(final String multipartReceiverUri) {
		this.multipartReceiverUri = multipartReceiverUri;
	}
	
	/**
	 * URI where outgoing payload messages are sent to
	 * <p>
	 * output only
	 */
	public void setPayloadDestinationUri(final String payloadDestinationUri) {
		this.payloadDestinationUri = payloadDestinationUri;
	}
	
	/**
	 * URI where outgoing ebxml response messages are sent to
	 * <p>
	 * output only
	 */
	public void setEbxmlResponseDestinationUri(final String ebxmlResponseDestinationUri) {
		this.ebxmlResponseDestinationUri = ebxmlResponseDestinationUri;
	}
	
	public void setIdempotentRepository(final IdempotentRepository<?> idempotentRepository) {
		this.idempotentRepository = idempotentRepository;
	}
	
	
	/**
	 * URI of internal route to publish outgoing payloads and to create the
	 * associated async ebxml responses
	 * <p>
	 * input and output (internal route)
	 */
	private String getPayloadPublisherUri() {
		return internalSedaUri("multipart-payload-publisher");
	}
	
	/**
	 * URI of internal route to send outgoing ebxml responses
	 * <p>
	 * input and output (internal route)
	 */
	private String getEbxmlResponseSenderUri() {
		return internalSedaUri("ebxml-response-sender");
	}	
	
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
				.setBody(property(PAYLOAD_PROPERTY))
				.to(ExchangePattern.InOnly, getPayloadPublisherUri())
			.end()
			
			.convertBodyTo(MultipartBody.class)
			
			.process(LOGGER.info(camelLogMsg("Receieved incoming spine multipart message")
				.eventName("receieved-spine-multipart-message")))
			
			.removeHeaders("*")
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
		from(getPayloadPublisherUri())
			// On failure - send ebxml delivery failure notification
			.onCompletion().onFailureOnly()
				.process(LOGGER.warn(camelLogMsg("Unable to publish spine multipart message")
						.documentId(header(Exchange.CORRELATION_ID))
						.ebxmlMessageId("${property.multipart-manifest?.messageData.messageId}")
						.service("${property.multipart-manifest?.service}")
						.action("${property.multipart-manifest?.action}")
						.receiverMHSPartyKey("${property.multipart-manifest?.toParty}")
						.eventName("publishing-spine-multipart-message-payload-error")))
			
				.setBody().property(MANIFEST_PROPERTY)
				.setBody().spel("#{body.generateDeliveryFailureNotification('Unable to deliver payload')}")
				.to(ExchangePattern.InOnly, getEbxmlResponseSenderUri())
			.end()
	
			// Publish payload message for processing - but only if not successfully processed already
			.idempotentConsumer(simple("${property." + MANIFEST_PROPERTY + ".messageData.messageId}"),
					idempotentRepository)
			.eager(false)
			.removeOnFailure(true)
			.skipDuplicate(false)
				// only publish if not handled already
				.filter(property(Exchange.DUPLICATE_MESSAGE).isNull())
					.process(LOGGER.warn(camelLogMsg("Publishing spine multipart message payload")
						.documentId(header(Exchange.CORRELATION_ID))
						.ebxmlMessageId("${property.multipart-manifest.messageData.messageId}")
						.service("${property.multipart-manifest.service}")
						.action("${property.multipart-manifest.action}")
						.receiverMHSPartyKey("${property.multipart-manifest.toParty}")
						.eventName("publishing-spine-multipart-message-payload")))
				
					.to(ExchangePattern.InOnly, payloadDestinationUri)
				.end()
	
				// always send ebxml ack (i.e. if previously acked or if publishing was successful)
				.setBody().property(MANIFEST_PROPERTY)
				.setBody(simple("${body.generateAcknowledgment()}"))
				.to(ExchangePattern.InOnly, getEbxmlResponseSenderUri())
			.end()
		.end();
	}
	
	/**
	 * Route to send outgoing ebxml acknowledgement and delivery fault async responses
	 */
	// TODO: does outgoing ack response need retry logic / error hander?
	private void configureEbxmlResponseSender() {
		from(getEbxmlResponseSenderUri())
			.doTry()
				.setProperty("ebxml").body()
				
				.process(LOGGER.warn(camelLogMsg("Sending async ebXml response")
					.documentId(header(Exchange.CORRELATION_ID))
					.ebxmlMessageId("${property.ebxml.messageData.messageId}")
					.ebxmlRefToMessageId("${property.ebxml.messageData.refToMessageId}")
					.service("${property.ebxml.service}")
					.action("${property.ebxml.action}")
					.receiverMHSPartyKey("${property.ebxml.toParty}")
					.soapAction(header("SOAPAction"))
					.eventName("sending-spine-ebxml-response")))
				
				.removeHeaders("*")
				.setHeader(Exchange.CONTENT_TYPE).constant("text/xml")
				.setHeader("SOAPAction").simple("${body.service}/${body.action}")
				.convertBodyTo(String.class)
				.to(ExchangePattern.InOut, ebxmlResponseDestinationUri)
			.endDoTry()
			.doCatch(Exception.class)
				.process(LOGGER.warn(camelLogMsg("Unable to send asyn ebXml response")
					.documentId(header(Exchange.CORRELATION_ID))
					.ebxmlMessageId("${property.ebxml.messageData.messageId}")
					.ebxmlRefToMessageId("${property.ebxml.messageData.refToMessageId}")
					.service("${property.ebxml.service}")
					.action("${property.ebxml.action}")
					.receiverMHSPartyKey("${property.ebxml.toParty}")
					.soapAction(header("SOAPAction"))
					.eventName("send-spine-ebxml-response-error")))
			.end()
		.end();
	}
}