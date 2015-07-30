package uk.nhs.ciao.transport.spine;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope;
import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope.ManifestReference;
import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelopeTypeConverter;
import uk.nhs.ciao.transport.spine.multipart.ContentType;
import uk.nhs.ciao.transport.spine.multipart.MultipartBody;
import uk.nhs.ciao.transport.spine.multipart.Part;

public class ItkAckReceiverRouteTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(ItkAckReceiverRouteTest.class);
	
	/**
	 * Temporary route while checking ITK ack behaviour
	 * will be moved into main source and SpineTransportRoutes
	 */
	public static class ItkAckReceiverRoute extends RouteBuilder {
		private final ProducerTemplate producerTemplate;
		
		public ItkAckReceiverRoute(final ProducerTemplate producerTemplate) {
			this.producerTemplate = Preconditions.checkNotNull(producerTemplate);
		}
		
		@Override
		public void configure() throws Exception {
			from("direct:itk-ack")
				.convertBodyTo(MultipartBody.class)
				.log("Converted to multipart body: ${body}")
				.process(new EbxmlManifestVerifier(producerTemplate))

				.split(simple("${body.parts[2]}")) // ITK payload
					.setExchangePattern(ExchangePattern.InOnly)
					.to("seda:store-itk-ack")
				.end()
				
				// HTTP sync response
				.setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
				.setBody(constant(""))
			.end();
				
			from("seda:store-itk-ack")
				// On failure - send ebxml delivery failure notification
				.onCompletion().onFailureOnly()
					// Using SpEL instead of simple to specify method parameters
					.setBody().spel("#{properties['ebxmlManifest'].generateDeliveryFailureNotification('Unable to deliver payload')}")
					.to("freemarker:uk/nhs/ciao/transport/spine/ebxml/ebxmlEnvelope.ftl")
					.to("mock:ebxml-ack")
				.end()

				// Publish ITK message for processing - but only if not successfully processed already
				.idempotentConsumer(simple("${property.ebxmlManifest.messageData.messageId}"),
						new MemoryIdempotentRepository())
				.eager(false)
				.removeOnFailure(true)
				.skipDuplicate(false)
					// only publish if not handled already
					.filter(property(Exchange.DUPLICATE_MESSAGE).isNull())
						.to("mock:itk-part")
					.end()

					// always send ebxml ack (i.e. if previously acked or if publishing was successful)
					.setBody(simple("${property.ebxmlManifest.generateAcknowledgment()}"))
					.to("freemarker:uk/nhs/ciao/transport/spine/ebxml/ebxmlEnvelope.ftl")
					.to("mock:ebxml-ack")
				.end()
			.end();
		}
	}
	
	public static class EbxmlManifestVerifier implements Processor {
		private final ProducerTemplate producerTemplate;
		
		public EbxmlManifestVerifier(final ProducerTemplate producerTemplate) {
			this.producerTemplate = Preconditions.checkNotNull(producerTemplate);
		}
		
		@Override
		public void process(final Exchange exchange) throws Exception {
			final MultipartBody multipartBody = exchange.getIn().getMandatoryBody(MultipartBody.class);
			final EbxmlEnvelope manifest = getManifest(multipartBody);
			storeManifest(exchange, manifest);
			
			final EbxmlEnvelope soapFault = verifyManifest(manifest, multipartBody);			
			if (soapFault != null) {
				final String xml = EbxmlEnvelopeTypeConverter.toString(producerTemplate, soapFault);
				
				final Message message = exchange.getIn();
				message.setBody(xml);
				message.setFault(true);
				message.setHeader(Exchange.HTTP_RESPONSE_CODE, "500");
				
				if (exchange.getPattern().isOutCapable()) {
                    exchange.setOut(message);
                }
			}
		}
		
		public EbxmlEnvelope getManifest(final MultipartBody multipartBody) throws Exception {
			final Part ebxmlPart = multipartBody.getParts().get(0);
			return ebxmlPart.getMandatoryBody(EbxmlEnvelope.class);
		}
		
		public void storeManifest(final Exchange exchange, final EbxmlEnvelope manifest) {
			exchange.setProperty("ebxmlManifest", manifest);
		}
		
		public EbxmlEnvelope verifyManifest(final EbxmlEnvelope manifest, final MultipartBody multipartBody) throws Exception {
			for (final ManifestReference reference: manifest.getManifestReferences()) {
				final String href = reference.getHref();
				if (!href.toLowerCase().startsWith("cid:")) {
					return clientFault(manifest, "Invalid href reference: " + href);
				}
				
				final String contentId = href.substring(4);
				final Part referencedPart =  multipartBody.findPartByContentId(contentId);
				if (referencedPart == null) {
					return clientFault(manifest, "Part referenced in manifest could not be found: " + reference);
				}
			}
			
			return null; // no fault
		}
		
		private EbxmlEnvelope clientFault(final EbxmlEnvelope manifest, final String description) {
			return manifest.generateSOAPFault(EbxmlEnvelope.ERROR_CODE_CLIENT, description);
		}
	}
	
	private CamelContext context;
	private ProducerTemplate producerTemplate;
	
	private MockEndpoint itkPartEndpoint;
	private MockEndpoint ebxmlAckEndpoint;
	
	@Before
	public void setup() throws Exception {
		context = new DefaultCamelContext();
		producerTemplate = new DefaultProducerTemplate(context);
		
		context.addRoutes(new ItkAckReceiverRoute(producerTemplate));
		
		context.start();
		producerTemplate.start();
		
		itkPartEndpoint = MockEndpoint.resolve(context, "mock:itk-part");
		ebxmlAckEndpoint = MockEndpoint.resolve(context, "mock:ebxml-ack");
	}
	
	@After
	public void teardown() {
		if (producerTemplate != null) {
			try {
				producerTemplate.stop();
			} catch (Exception e) {
				LOGGER.warn("Unable to stop producerTemplate", e);
			}
		}
		
		if (context != null) {
			try {
				context.stop();
			} catch (Exception e) {
				LOGGER.warn("Unable to stop context", e);
			}
		}
	}
	
	@Test
	public void testSingleMessage() throws Exception {
		final MultipartBody body = createExampleRequest();
		
		final EbxmlEnvelope syncResponse = sendItkAck(body);
		
		// Sync response should not be a fault
		Assert.assertNull("SOAPFault", syncResponse);
		
		// ITK message should be extracted and published for processing
		itkPartEndpoint.expectedMessageCount(1);
		itkPartEndpoint.expectedBodyReceived().constant(getItkBody(body));
		
		// async ack should be sent (after ITK message is published)
		ebxmlAckEndpoint.expectedMessageCount(1);
		
		ebxmlAckEndpoint.assertIsSatisfied(1000);
		itkPartEndpoint.assertIsSatisfied();
		
		// Check that the response is an ack
		final EbxmlEnvelope ack = ebxmlAckEndpoint.getExchanges().get(0).getIn().getMandatoryBody(EbxmlEnvelope.class);
		Assert.assertTrue("Acknowledgment", ack.isAcknowledgment());
	}
	
	@Test
	public void testDuplicateDetection() throws Exception {
		final MultipartBody body = createExampleRequest();
		
		EbxmlEnvelope syncResponse = sendItkAck(body);
		
		// Sync response should not be a fault
		Assert.assertNull("SOAPFault", syncResponse);
		
		// ITK message should be extracted and published for processing
		itkPartEndpoint.expectedMessageCount(1);
		itkPartEndpoint.expectedBodyReceived().constant(getItkBody(body));
		
		// async ack should be sent (after ITK message is published)
		ebxmlAckEndpoint.expectedMessageCount(1);
		
		ebxmlAckEndpoint.assertIsSatisfied(1000);
		itkPartEndpoint.assertIsSatisfied();
		
		// Check that the response is an ack
		EbxmlEnvelope ack = ebxmlAckEndpoint.getExchanges().get(0).getIn().getMandatoryBody(EbxmlEnvelope.class);
		Assert.assertTrue("Acknowledgment", ack.isAcknowledgment());
		
		// Check duplicate detection by resending message
		ebxmlAckEndpoint.reset();
		itkPartEndpoint.reset();
		syncResponse = sendItkAck(body);
		
		// Sync response should not be a fault
		Assert.assertNull("SOAPFault", syncResponse);
		
		// ITK message should not be published again
		itkPartEndpoint.expectedMessageCount(0);
		
		// However an async ack should be sent
		ebxmlAckEndpoint.expectedMessageCount(1);
		
		ebxmlAckEndpoint.assertIsSatisfied(1000);
		itkPartEndpoint.assertIsSatisfied();
		
		// Check that the response is an ack
		ack = ebxmlAckEndpoint.getExchanges().get(0).getIn().getMandatoryBody(EbxmlEnvelope.class);
		Assert.assertTrue("Acknowledgment", ack.isAcknowledgment());
	}
	
	@Test
	public void testNackIsSentWhenItkMessageCannotBePublished() throws Exception {
		final MultipartBody body = createExampleRequest();
		
		itkPartEndpoint.whenAnyExchangeReceived(new Processor() {
			@Override
			public void process(final Exchange exchange) throws Exception {
				throw new Exception("Simulating message delivery failure");
			}
		});
		
		final EbxmlEnvelope syncResponse = sendItkAck(body);
		
		// Sync response should not be a fault
		Assert.assertNull("SOAPFault", syncResponse);
		
		// async ack should be sent (after ITK message is published)
		ebxmlAckEndpoint.expectedMessageCount(1);
		
		ebxmlAckEndpoint.assertIsSatisfied(1000);
		
		// Check that the response was a nack
		final EbxmlEnvelope nack = ebxmlAckEndpoint.getExchanges().get(0).getIn().getMandatoryBody(EbxmlEnvelope.class);
		Assert.assertTrue("DeliveryFailure", nack.isDeliveryFailure());
	}
	
	private MultipartBody createExampleRequest() throws Exception {
		final MultipartBody body = new MultipartBody();

		final EbxmlEnvelope manifest = new EbxmlEnvelope();
		manifest.applyDefaults();
		
		final Part ebxmlPart = body.addPart("text/xml", manifest);
		
		final Part hl7Part = body.addPart("application/xml; charset=UTF-8", "<COPC_IN000001GB01 xmlns=\"urn:hl7-org:v3\" />");
		final ManifestReference hl7Reference = manifest.addManifestReference();
		hl7Reference.setHref("cid:" + hl7Part.getContentId());
		hl7Reference.setHl7(true);
		hl7Reference.setDescription("HL7 payload");
		
		final Part itkPart = body.addPart("text/xml", "<itk:DistributionEnvelope xmlns:itk=\"urn:nhs-itk:ns:201005\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" />");
		final ManifestReference itkReference = manifest.addManifestReference();
		itkReference.setHref("cid:" + itkPart.getContentId());
		itkReference.setDescription("ITK Trunk Message");

		ebxmlPart.setBody(serialize(manifest));
		
		return body;
	}
	
	private String getItkBody(final MultipartBody body) throws Exception {
		return body.getParts().get(2).getMandatoryBody(String.class);
	}
	
	private String serialize(final EbxmlEnvelope envelope) throws Exception {
		return EbxmlEnvelopeTypeConverter.toString(producerTemplate, envelope);
	}
	
	private EbxmlEnvelope sendItkAck(final MultipartBody body) throws Exception {
		final Exchange exchange = new DefaultExchange(context);
		exchange.setPattern(ExchangePattern.InOut);
		exchange.getIn().setBody(body, String.class); // convert the body

		final ContentType contentType = new ContentType("multipart", "related");
		contentType.setBoundary(body.getBoundary());
		contentType.setStart(body.getParts().get(0).getRawContentId());
		exchange.getIn().setHeader(Exchange.CONTENT_TYPE, contentType.toString());
		
		producerTemplate.send("direct:itk-ack", exchange);
		if (exchange.getException() != null) {
			throw exchange.getException();
		} else if (exchange.getOut().isFault()) {
			final EbxmlEnvelope fault = exchange.getOut().getMandatoryBody(EbxmlEnvelope.class);
			return fault;
		}
		
		// no fault
		return null; 
	}
}
