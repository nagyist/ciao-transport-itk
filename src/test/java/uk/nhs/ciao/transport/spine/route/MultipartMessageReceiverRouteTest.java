package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import uk.nhs.ciao.camel.CamelUtils;
import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope;
import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope.ManifestReference;
import uk.nhs.ciao.transport.spine.multipart.ContentType;
import uk.nhs.ciao.transport.spine.multipart.MultipartBody;
import uk.nhs.ciao.transport.spine.multipart.Part;

public class MultipartMessageReceiverRouteTest {
	private CamelContext context;
	private ProducerTemplate producerTemplate;
	
	private MockEndpoint payloadReceiver;
	private MockEndpoint ebxmlResponseReceiver;
	
	@Before
	public void setup() throws Exception {
		context = new DefaultCamelContext();
		producerTemplate = new DefaultProducerTemplate(context);
		
		final MultipartMessageReceiverRoute route = new MultipartMessageReceiverRoute();
		route.setMultipartReceiverUri("direct:multipart-receiver");
		route.setPayloadDestinationUri("mock:multipart-payloads");
		route.setEbxmlResponseDestinationUri("mock:ebxml-responses");
		route.setIdempotentRepository(new MemoryIdempotentRepository());
		context.addRoutes(route);
		
		context.start();
		producerTemplate.start();
		
		payloadReceiver = MockEndpoint.resolve(context, "mock:multipart-payloads");
		ebxmlResponseReceiver = MockEndpoint.resolve(context, "mock:ebxml-responses");
	}
	
	@After
	public void teardown() {
		CamelUtils.stopQuietly(producerTemplate, context);
	}
	
	@Test
	public void testSingleMessage() throws Exception {
		final MultipartBody body = createExampleRequest();
		
		final EbxmlEnvelope syncResponse = sendMultipartMessage(body);
		
		// Sync response should not be a fault
		Assert.assertNull("SOAPFault", syncResponse);
		
		expectSinglePayload(body);
		
		// async ack should be sent (after payload message is published)
		ebxmlResponseReceiver.expectedMessageCount(1);
		
		ebxmlResponseReceiver.assertIsSatisfied(1000);
		payloadReceiver.assertIsSatisfied();
		
		// Check that the response is an ack
		final EbxmlEnvelope ack = getFirstEbxmlResponse();
		Assert.assertTrue("Acknowledgment", ack.isAcknowledgment());
	}
	
	@Test
	public void testDuplicateDetection() throws Exception {
		final MultipartBody body = createExampleRequest();
		
		EbxmlEnvelope syncResponse = sendMultipartMessage(body);
		
		// Sync response should not be a fault
		Assert.assertNull("SOAPFault", syncResponse);
		
		// Payload message should be extracted and published for processing
		expectSinglePayload(body);
		
		// async ack should be sent (after payload message is published)
		ebxmlResponseReceiver.expectedMessageCount(1);
		
		ebxmlResponseReceiver.assertIsSatisfied(1000);
		payloadReceiver.assertIsSatisfied();
		
		// Check that the response is an ack
		EbxmlEnvelope ack = getFirstEbxmlResponse();
		Assert.assertTrue("Acknowledgment", ack.isAcknowledgment());
		
		// Check duplicate detection by resending message
		ebxmlResponseReceiver.reset();
		payloadReceiver.reset();
		syncResponse = sendMultipartMessage(body);
		
		// Sync response should not be a fault
		Assert.assertNull("SOAPFault", syncResponse);
		
		// Payload message should not be published again
		payloadReceiver.expectedMessageCount(0);
		
		// However an async ack should be sent
		ebxmlResponseReceiver.expectedMessageCount(1);
		
		ebxmlResponseReceiver.assertIsSatisfied(1000);
		payloadReceiver.assertIsSatisfied();
		
		// Check that the response is an ack
		ack = getFirstEbxmlResponse();
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
	
	@Test(expected=Exception.class)
	public void testHttpErrorIsSentWhenMultipartBodyIsInvalid() throws Exception {
		// Send a corrupted request
		final Exchange exchange = new DefaultExchange(context);
		exchange.getIn().setBody("this is not a valid multipart-body");
		
		// an HTTP error is expected
		sendMultipartMessage(exchange);
	}
	
	@Test
	public void testDeliveryFailureIsSentWhenPayloadPublishingFailsWithAnException() throws Exception {
		final MultipartBody body = createExampleRequest();
		
		payloadReceiver.whenAnyExchangeReceived(new Processor() {
			@Override
			public void process(final Exchange exchange) throws Exception {
				throw new Exception("Simulating message delivery failure");
			}
		});
		
		final EbxmlEnvelope syncResponse = sendMultipartMessage(body);
		
		// Sync response should not be a fault
		Assert.assertNull("SOAPFault", syncResponse);
		
		// async nack should be sent (after payload message publishing is attempted)
		ebxmlResponseReceiver.expectedMessageCount(1);
		
		ebxmlResponseReceiver.assertIsSatisfied(1000);
		
		// Check that the response was a nack
		final EbxmlEnvelope nack = getFirstEbxmlResponse();
		Assert.assertTrue("DeliveryFailure", nack.isDeliveryFailure());
		Assert.assertTrue("Warning severity", nack.getError().isWarning());
	}
	
	@Test
	public void testDeliveryFailureIsSentWhenPayloadPublishingFailsWithAFaultMessage() throws Exception {
		final MultipartBody body = createExampleRequest();
		
		payloadReceiver.whenAnyExchangeReceived(new Processor() {
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
		ebxmlResponseReceiver.expectedMessageCount(1);
		
		ebxmlResponseReceiver.assertIsSatisfied(1000);
		
		// Check that the response was a nack
		final EbxmlEnvelope nack = getFirstEbxmlResponse();
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
	
	private void expectSinglePayload(final MultipartBody body) throws Exception {
		payloadReceiver.expectedMessageCount(1);
		payloadReceiver.expectedBodyReceived().constant(getPayloadBody(body));
	}
	
	private String getPayloadBody(final MultipartBody body) throws Exception {
		return body.getParts().get(2).getMandatoryBody(String.class);
	}
	
	private String serialize(final EbxmlEnvelope envelope) {
		return context.getTypeConverter().convertTo(String.class, envelope);
	}
	
	private EbxmlEnvelope getFirstEbxmlResponse() throws InvalidPayloadException {
		return ebxmlResponseReceiver.getExchanges().get(0).getIn().getMandatoryBody(EbxmlEnvelope.class);
	}
	
	private EbxmlEnvelope sendMultipartMessage(final MultipartBody body) throws Exception {
		final Exchange exchange = new DefaultExchange(context);
		exchange.getIn().setBody(body, String.class); // convert the body

		final ContentType contentType = new ContentType("multipart", "related");
		contentType.setBoundary(body.getBoundary());
		contentType.setStart(body.getParts().get(0).getRawContentId());
		exchange.getIn().setHeader(Exchange.CONTENT_TYPE, contentType.toString());
		
		return sendMultipartMessage(exchange);
	}
	
	private EbxmlEnvelope sendMultipartMessage(final Exchange exchange) throws Exception {
		exchange.setPattern(ExchangePattern.InOut);
		
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
