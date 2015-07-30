package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope;
import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope.ManifestReference;
import uk.nhs.ciao.transport.spine.multipart.ContentType;
import uk.nhs.ciao.transport.spine.multipart.MultipartBody;
import uk.nhs.ciao.transport.spine.multipart.Part;

public class MultipartMessageReceiverRouteTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(MultipartMessageReceiverRouteTest.class);
	
	private CamelContext context;
	private ProducerTemplate producerTemplate;
	
	private MockEndpoint payloadReceiverEndpoint;
	private MockEndpoint ackSenderEndpoint;
	
	@Before
	public void setup() throws Exception {
		context = new DefaultCamelContext();
		producerTemplate = new DefaultProducerTemplate(context);
		
		context.addRoutes(new MultipartMessageReceiverRoute());
		
		context.start();
		producerTemplate.start();
		
		payloadReceiverEndpoint = MockEndpoint.resolve(context, "mock:multipart-payload-receiver");
		ackSenderEndpoint = MockEndpoint.resolve(context, "mock:multipart-ack-sender");
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
		
		final EbxmlEnvelope syncResponse = sendMultipartMessage(body);
		
		// Sync response should not be a fault
		Assert.assertNull("SOAPFault", syncResponse);
		
		// Payload message should be extracted and published for processing
		payloadReceiverEndpoint.expectedMessageCount(1);
		payloadReceiverEndpoint.expectedBodyReceived().constant(getPayloadBody(body));
		
		// async ack should be sent (after payload message is published)
		ackSenderEndpoint.expectedMessageCount(1);
		
		ackSenderEndpoint.assertIsSatisfied(1000);
		payloadReceiverEndpoint.assertIsSatisfied();
		
		// Check that the response is an ack
		final EbxmlEnvelope ack = ackSenderEndpoint.getExchanges().get(0).getIn().getMandatoryBody(EbxmlEnvelope.class);
		Assert.assertTrue("Acknowledgment", ack.isAcknowledgment());
	}
	
	@Test
	public void testDuplicateDetection() throws Exception {
		final MultipartBody body = createExampleRequest();
		
		EbxmlEnvelope syncResponse = sendMultipartMessage(body);
		
		// Sync response should not be a fault
		Assert.assertNull("SOAPFault", syncResponse);
		
		// Payload message should be extracted and published for processing
		payloadReceiverEndpoint.expectedMessageCount(1);
		payloadReceiverEndpoint.expectedBodyReceived().constant(getPayloadBody(body));
		
		// async ack should be sent (after payload message is published)
		ackSenderEndpoint.expectedMessageCount(1);
		
		ackSenderEndpoint.assertIsSatisfied(1000);
		payloadReceiverEndpoint.assertIsSatisfied();
		
		// Check that the response is an ack
		EbxmlEnvelope ack = ackSenderEndpoint.getExchanges().get(0).getIn().getMandatoryBody(EbxmlEnvelope.class);
		Assert.assertTrue("Acknowledgment", ack.isAcknowledgment());
		
		// Check duplicate detection by resending message
		ackSenderEndpoint.reset();
		payloadReceiverEndpoint.reset();
		syncResponse = sendMultipartMessage(body);
		
		// Sync response should not be a fault
		Assert.assertNull("SOAPFault", syncResponse);
		
		// Payload message should not be published again
		payloadReceiverEndpoint.expectedMessageCount(0);
		
		// However an async ack should be sent
		ackSenderEndpoint.expectedMessageCount(1);
		
		ackSenderEndpoint.assertIsSatisfied(1000);
		payloadReceiverEndpoint.assertIsSatisfied();
		
		// Check that the response is an ack
		ack = ackSenderEndpoint.getExchanges().get(0).getIn().getMandatoryBody(EbxmlEnvelope.class);
		Assert.assertTrue("Acknowledgment", ack.isAcknowledgment());
	}
	
	@Test
	public void testSOAPFaultIsSentWhenManifestIsInvalid() throws Exception {
		final MultipartBody body = createExampleRequest();

		// Update content IDs to 'break' the manifest
		body.getParts().get(1).setContentId("<invalid-content-id>");
		
		final EbxmlEnvelope syncResponse = sendMultipartMessage(body);
		
		// Sync response should be a fault
		Assert.assertNotNull("SOAPFault", syncResponse);
		Assert.assertTrue("SOAPFault", syncResponse.isSOAPFault());
		Assert.assertTrue("Error severity", syncResponse.getError().isError());
	}
	
	@Test
	public void testDeliveryFailureIsSentWhenPayloadPublishingFailsWithAnException() throws Exception {
		final MultipartBody body = createExampleRequest();
		
		payloadReceiverEndpoint.whenAnyExchangeReceived(new Processor() {
			@Override
			public void process(final Exchange exchange) throws Exception {
				throw new Exception("Simulating message delivery failure");
			}
		});
		
		final EbxmlEnvelope syncResponse = sendMultipartMessage(body);
		
		// Sync response should not be a fault
		Assert.assertNull("SOAPFault", syncResponse);
		
		// async nack should be sent (after payload message publishing is attempted)
		ackSenderEndpoint.expectedMessageCount(1);
		
		ackSenderEndpoint.assertIsSatisfied(1000);
		
		// Check that the response was a nack
		final EbxmlEnvelope nack = ackSenderEndpoint.getExchanges().get(0).getIn().getMandatoryBody(EbxmlEnvelope.class);
		Assert.assertTrue("DeliveryFailure", nack.isDeliveryFailure());
		Assert.assertTrue("Warning severity", nack.getError().isWarning());
	}
	
	@Test
	public void testDeliveryFailureIsSentWhenPayloadPublishingFailsWithAFaultMessage() throws Exception {
		final MultipartBody body = createExampleRequest();
		
		payloadReceiverEndpoint.whenAnyExchangeReceived(new Processor() {
			@Override
			public void process(final Exchange exchange) throws Exception {
				exchange.getOut().setBody("Simulating message delivery failure");
				exchange.getOut().setFault(true);
			}
		});
		
		final EbxmlEnvelope syncResponse = sendMultipartMessage(body);
		
		// Sync response should not be a fault
		Assert.assertNull("SOAPFault", syncResponse);
		
		// async nack should be sent (after payload message publishing is attempted)
		ackSenderEndpoint.expectedMessageCount(1);
		
		ackSenderEndpoint.assertIsSatisfied(1000);
		
		// Check that the response was a nack
		final EbxmlEnvelope nack = ackSenderEndpoint.getExchanges().get(0).getIn().getMandatoryBody(EbxmlEnvelope.class);
		Assert.assertTrue("DeliveryFailure", nack.isDeliveryFailure());
		Assert.assertTrue("Warning severity", nack.getError().isWarning());
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
		
		final Part payloadPart = body.addPart("text/xml", "<itk:DistributionEnvelope xmlns:itk=\"urn:nhs-itk:ns:201005\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" />");
		final ManifestReference itkReference = manifest.addManifestReference();
		itkReference.setHref("cid:" + payloadPart.getContentId());
		itkReference.setDescription("ITK Trunk Message");

		ebxmlPart.setBody(serialize(manifest));
		
		return body;
	}
	
	private String getPayloadBody(final MultipartBody body) throws Exception {
		return body.getParts().get(2).getMandatoryBody(String.class);
	}
	
	private String serialize(final EbxmlEnvelope envelope) {
		return context.getTypeConverter().convertTo(String.class, envelope);
	}
	
	private EbxmlEnvelope sendMultipartMessage(final MultipartBody body) throws Exception {
		final Exchange exchange = new DefaultExchange(context);
		exchange.setPattern(ExchangePattern.InOut);
		exchange.getIn().setBody(body, String.class); // convert the body

		final ContentType contentType = new ContentType("multipart", "related");
		contentType.setBoundary(body.getBoundary());
		contentType.setStart(body.getParts().get(0).getRawContentId());
		exchange.getIn().setHeader(Exchange.CONTENT_TYPE, contentType.toString());
		
		producerTemplate.send("direct:multipart-receiver", exchange);
		if (exchange.getException() != null) {
			throw exchange.getException();
		} else if (exchange.getOut().isFault()) {
			final EbxmlEnvelope fault = exchange.getOut().getMandatoryBody(EbxmlEnvelope.class);
			Assert.assertEquals("HTTP status code", "500", exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE, String.class));
			return fault;
		}
		
		// no fault
		Assert.assertEquals("HTTP status code", "200", exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE, String.class));
		return null; 
	}
}
