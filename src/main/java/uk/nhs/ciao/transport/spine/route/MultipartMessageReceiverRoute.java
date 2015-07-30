package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import uk.nhs.ciao.transport.spine.multipart.MultipartBody;

import com.google.common.base.Preconditions;

/**
 * Temporary route while checking ITK ack behaviour
 * will be moved into main source and SpineTransportRoutes
 */
public class MultipartMessageReceiverRoute extends RouteBuilder {
	private static final Logger LOGGER = LoggerFactory.getLogger(MultipartMessageReceiverRoute.class);
	
	private final ProducerTemplate producerTemplate;
	
	@Autowired
	public MultipartMessageReceiverRoute(final ProducerTemplate producerTemplate) {
		this.producerTemplate = Preconditions.checkNotNull(producerTemplate);
	}

	
	// TODO: Make in/out route URLs configurable
	// TODO: does outgoing ack response need retry logic / error hander?
	
	@Override
	public void configure() throws Exception {		
		/*
		 * Route to receive a multi-part message, process the manifest and send the sync response
		 * (no content, or SOAPFault)
		 * <p>
		 * The payload is extracted and sent for publishing via a separate route
		 */
		from("direct:multipart-receiver")

			// Start publishing the payload after the initial HTTP response has been sent
			.onCompletion().onCompleteOnly()
				.setExchangePattern(ExchangePattern.InOnly)
				.setBody(property("multipart-payload"))
				.to("seda:store-multipart-payload")
			.end()
			
			.convertBodyTo(MultipartBody.class)
			.log(LoggingLevel.DEBUG, LOGGER, "Converted to multipart body: ${body}")
			.process(new EbxmlManifestVerifier(producerTemplate))

			// Store the payload in a property so it can be published after the main response is sent
			.setProperty("multipart-payload").spel("#{body.getParts().get(2).getBody()}")
			
			// HTTP sync response
			.setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
			.setBody(constant(""))
		.end();
		
		/*
		 * Route to publish the payload and send the corresponding async ebxml ack
		 */
		from("seda:store-multipart-payload")
			// On failure - send ebxml delivery failure notification
			.onCompletion().onFailureOnly()
				// Using SpEL instead of simple to specify method parameters
				.setBody().spel("#{properties['ebxmlManifest'].generateDeliveryFailureNotification('Unable to deliver payload')}")
				.to("freemarker:uk/nhs/ciao/transport/spine/ebxml/ebxmlEnvelope.ftl")
				.to("mock:multipart-ack-sender")
			.end()

			// Publish ITK message for processing - but only if not successfully processed already
			.idempotentConsumer(simple("${property.ebxmlManifest.messageData.messageId}"),
					new MemoryIdempotentRepository())
			.eager(false)
			.removeOnFailure(true)
			.skipDuplicate(false)
				// only publish if not handled already
				.filter(property(Exchange.DUPLICATE_MESSAGE).isNull())
					.to("mock:multipart-payload-receiver")
				.end()

				// always send ebxml ack (i.e. if previously acked or if publishing was successful)
				.setBody(simple("${property.ebxmlManifest.generateAcknowledgment()}"))
				.to("freemarker:uk/nhs/ciao/transport/spine/ebxml/ebxmlEnvelope.ftl")
				.to("mock:multipart-ack-sender")
			.end()
		.end();
	}
}