package uk.nhs.ciao.transport.spine.route;

import java.util.List;
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;

import com.google.common.collect.Lists;

import uk.nhs.ciao.camel.CamelUtils;
import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope;
import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope.ManifestReference;
import uk.nhs.ciao.transport.spine.multipart.ContentType;
import uk.nhs.ciao.transport.spine.multipart.MultipartBody;
import uk.nhs.ciao.transport.spine.multipart.Part;

/**
 * Unit tests for {@link MultipartMessageSenderRoute}
 */
public class MultipartMessageSenderRouteTest {
	private CamelContext context;
	private ProducerTemplate producerTemplate;
	
	private MockEndpoint messageDestination;
	
	@Before
	public void setup() throws Exception {
		final SimpleRegistry registry = new SimpleRegistry();
		
		final SpringTransactionPolicy propegationRequiresNew = new SpringTransactionPolicy();
		propegationRequiresNew.setTransactionManager(Mockito.mock(PlatformTransactionManager.class, Mockito.RETURNS_MOCKS));
		propegationRequiresNew.setPropagationBehaviorName("PROPAGATION_NOT_SUPPORTED");
		registry.put("PROPAGATION_NOT_SUPPORTED", propegationRequiresNew);
		
		context = new DefaultCamelContext(registry);
		producerTemplate = new DefaultProducerTemplate(context);
		
		final MultipartMessageSenderRoute route = new MultipartMessageSenderRoute();
		route.setInternalRoutePrefix("multipart-message-sender");
		route.setMultipartMessageSenderUri("direct:multipart-message-sender");
		route.setMultipartMessageDestinationUri("mock:multipart-message-destination");
		route.setEbxmlAckReceiverUri("seda:multipart-ack-receiver");
		
		context.addRoutes(route);
		
		context.start();
		producerTemplate.start();
		
		messageDestination = MockEndpoint.resolve(context, "mock:multipart-message-destination");
	}
	
	@After
	public void teardown() {
		CamelUtils.stopQuietly(producerTemplate, context);
	}
	
	@Test
	public void testResponseIsPublishedOnAsyncAck() throws Exception {
		final MultipartBody exampleRequest = createExampleRequest();
		
		final List<String> problems = Lists.newCopyOnWriteArrayList();
		messageDestination.expectedMessageCount(1);
		messageDestination.whenAnyExchangeReceived(new Processor() {
			@Override
			public void process(final Exchange exchange) throws Exception {
				if (ExchangePattern.InOut != exchange.getPattern()) {
					problems.add("Expected request-response pattern - received: " + exchange.getPattern());
				}
				
				final MultipartBody body = exchange.getIn().getMandatoryBody(MultipartBody.class);
				final EbxmlEnvelope manifest = body.getParts().get(0).getMandatoryBody(EbxmlEnvelope.class);
				final EbxmlEnvelope ack = manifest.generateAcknowledgment();
				sendAsyncResponse(ack);
			}
		});
		
		sendMultipartMessage(exampleRequest);
		
		Assert.assertTrue("Problems: " + problems, problems.isEmpty());
		messageDestination.assertIsSatisfied();
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
	
	private String serialize(final EbxmlEnvelope envelope) {
		return context.getTypeConverter().convertTo(String.class, envelope);
	}
	
	private <T> T deserialize( final Class<T> type, final String body) {
		return context.getTypeConverter().convertTo(type, body);
	}
	
	private void sendMultipartMessage(final MultipartBody body) throws Exception {
		final Exchange exchange = new DefaultExchange(context);
		exchange.getIn().setBody(body, String.class); // convert the body

		final EbxmlEnvelope envelope = deserialize(EbxmlEnvelope.class, body.getParts().get(0).getBody(String.class));
		exchange.getIn().setHeader("JMSMessageID", UUID.randomUUID().toString());
		exchange.getIn().setHeader(Exchange.CORRELATION_ID, envelope.getMessageData().getMessageId());
		
		final ContentType contentType = new ContentType("multipart", "related");
		contentType.setBoundary(body.getBoundary());
		contentType.setStart(body.getParts().get(0).getRawContentId());
		exchange.getIn().setHeader(Exchange.CONTENT_TYPE, contentType.toString());
		
		producerTemplate.send("direct:multipart-message-sender", exchange);
	}
	
	private void sendAsyncResponse(final EbxmlEnvelope envelope) {
		final Exchange exchange = new DefaultExchange(context);
		exchange.getIn().setBody(envelope, String.class); // convert the body
		
		// Set properties / headers equivalent to the EbxmlAckReceiever
		exchange.setProperty("envelope", envelope);
		exchange.getIn().setHeader("JMSMessageID", UUID.randomUUID().toString());
		exchange.getIn().setHeader(Exchange.CORRELATION_ID, envelope.getMessageData().getRefToMessageId());
		
		producerTemplate.send("seda:multipart-ack-receiver", exchange);
	}
}
