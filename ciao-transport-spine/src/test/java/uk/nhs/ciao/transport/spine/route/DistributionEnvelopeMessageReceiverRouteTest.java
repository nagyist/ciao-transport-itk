package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.unitils.reflectionassert.ReflectionAssert;

import com.google.common.base.Throwables;

import uk.nhs.ciao.camel.CamelUtils;
import uk.nhs.ciao.transport.itk.envelope.Address;
import uk.nhs.ciao.transport.itk.envelope.DistributionEnvelope;
import uk.nhs.ciao.transport.itk.envelope.InfrastructureResponse;
import uk.nhs.ciao.transport.itk.envelope.InfrastructureResponseFactory;
import uk.nhs.ciao.transport.itk.envelope.DistributionEnvelope.ManifestItem;

public class DistributionEnvelopeMessageReceiverRouteTest {
	private CamelContext context;
	private ProducerTemplate producerTemplate;
	
	private MockEndpoint payloadReceiver;
	private MockEndpoint infrastructureResponseReceiver;
	
	@Before
	public void setup() throws Exception {
		final SimpleRegistry registry = new SimpleRegistry();
		
		context = new DefaultCamelContext(registry);
		producerTemplate = new DefaultProducerTemplate(context);
		
		final SpringTransactionPolicy propegationRequiresNew = new SpringTransactionPolicy();
		propegationRequiresNew.setTransactionManager(Mockito.mock(PlatformTransactionManager.class, Mockito.RETURNS_MOCKS));
		propegationRequiresNew.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
		registry.put("PROPAGATION_REQUIRES_NEW", propegationRequiresNew);
		
		final SpringTransactionPolicy propegationRequired= new SpringTransactionPolicy();
		propegationRequired.setTransactionManager(Mockito.mock(PlatformTransactionManager.class, Mockito.RETURNS_MOCKS));
		propegationRequired.setPropagationBehaviorName("PROPAGATION_REQUIRED");
		registry.put("PROPAGATION_REQUIRED", propegationRequired);
		
		final DistributionEnvelopeReceiverRoute route = new DistributionEnvelopeReceiverRoute();
		route.setDistributionEnvelopeReceiverUri("direct:distribution-envelope-receiver");
		route.setItkMessageReceiverUri("mock:distribution-envelope-payloads");
		route.setDistributionEnvelopeSenderUri("mock:infrastructure-responses");
		route.setIdempotentRepository(new MemoryIdempotentRepository());
		route.setInfrastructureResponseFactory(new InfrastructureResponseFactory());
		
		context.addRoutes(route);
		
		context.start();
		producerTemplate.start();
		
		payloadReceiver = MockEndpoint.resolve(context, "mock:distribution-envelope-payloads");
		infrastructureResponseReceiver = MockEndpoint.resolve(context, "mock:infrastructure-responses");
	}
	
	@After
	public void teardown() {
		CamelUtils.stopQuietly(producerTemplate, context);
	}
	
	@Test
	public void testSingleMessage() throws Exception {
		final boolean isInfAck = false;
		final DistributionEnvelope envelope = createExampleRequest(isInfAck);
		envelope.getHandlingSpec().setInfrastructureAckRequested(true);
		
		sendDistributionEnvelope(envelope);
		
		expectSinglePayload(envelope);
		
		// async ack should be sent (after payload message is published)
		infrastructureResponseReceiver.expectedMessageCount(1);
		
		infrastructureResponseReceiver.assertIsSatisfied(1000);
		payloadReceiver.assertIsSatisfied();
		
		// Check that the response is an ack
		final DistributionEnvelope ack = getFirstInfrastructureResponse();
		Assert.assertTrue("Acknowledgment", ack.getHandlingSpec().isInfrastructureAck());
	}
	
	@Test
	public void testDuplicateDetection() throws Exception {
		final boolean isInfAck = false;
		final DistributionEnvelope envelope = createExampleRequest(isInfAck);
		envelope.getHandlingSpec().setInfrastructureAckRequested(true);
		
		sendDistributionEnvelope(envelope);
		
		// Payload message should be extracted and published for processing
		expectSinglePayload(envelope);
		
		// async ack should be sent (after payload message is published)
		infrastructureResponseReceiver.expectedMessageCount(1);
		
		infrastructureResponseReceiver.assertIsSatisfied(1000);
		payloadReceiver.assertIsSatisfied();
		
		// Check that the response is an ack
		DistributionEnvelope ack = getFirstInfrastructureResponse();
		Assert.assertTrue("Acknowledgment", ack.getHandlingSpec().isInfrastructureAck());
		
		// Check duplicate detection by resending message
		infrastructureResponseReceiver.reset();
		payloadReceiver.reset();
		sendDistributionEnvelope(envelope);
		
		// Payload message should not be published again
		payloadReceiver.expectedMessageCount(0);
		
		// However an async ack should be sent
		infrastructureResponseReceiver.expectedMessageCount(1);
		
		infrastructureResponseReceiver.assertIsSatisfied(1000);
		payloadReceiver.assertIsSatisfied();
		
		// Check that the response is an ack
		ack = getFirstInfrastructureResponse();
		Assert.assertTrue("Acknowledgment", ack.getHandlingSpec().isInfrastructureAck());
	}
	
	@Test
	public void testInfResponseIsNotSentIfMessageIsInfResponse() throws Exception {
		final boolean isInfAck = true;
		final DistributionEnvelope envelope = createExampleRequest(isInfAck);
		envelope.getHandlingSpec().setInfrastructureAckRequested(true); // an inf ack should not request an inf ack response!
		
		sendDistributionEnvelope(envelope);
		
		payloadReceiver.expectedMessageCount(1);
		final InfrastructureResponse payloadBody = toInfrastructureResponse(getPayloadBody(envelope));
		payloadReceiver.expectedMessagesMatches(new Predicate() {
			@Override
			public boolean matches(final Exchange exchange) {
				final DistributionEnvelope received = exchange.getIn().getBody(DistributionEnvelope.class);
				Assert.assertNotNull("Expected DistributionEnvelope", received);
				
				Assert.assertEquals("Expected 1 payload", received.getPayloads().size(), 1);
				final InfrastructureResponse receievedPayload = toInfrastructureResponse(received.getPayloads().get(0).getBody());
				ReflectionAssert.assertReflectionEquals("Expected payload body", payloadBody, receievedPayload);
				
				return true;
			}
		});
		
		// async ack (although requested) should not be sent!
		infrastructureResponseReceiver.expectedMessageCount(0);
		
		payloadReceiver.assertIsSatisfied();
		infrastructureResponseReceiver.assertIsSatisfied(1000);
	}
	
	@Test
	public void testDeliveryFailureIsSentWhenManifestIsInvalid() throws Exception {
		final boolean isInfAck = false;
		final DistributionEnvelope envelope = createExampleRequest(isInfAck);
		envelope.getHandlingSpec().setInfrastructureAckRequested(true);

		// Removing payloads to break the manifest
		envelope.getPayloads().clear();
		
		sendDistributionEnvelope(envelope);
		
		// async nack should be sent (after payload message publishing is attempted)
		infrastructureResponseReceiver.expectedMessageCount(1);
		
		infrastructureResponseReceiver.assertIsSatisfied(1000);
		
		// Check that the response was a nack
		final DistributionEnvelope nack = getFirstInfrastructureResponse();
		Assert.assertTrue("isInfrastructureAck", nack.getHandlingSpec().isInfrastructureAck());
		Assert.assertEquals("single payload", 1, nack.getPayloads().size());
		
		final InfrastructureResponse response = toInfrastructureResponse(nack.getPayloads().get(0).getBody());
		Assert.assertEquals("Warning response", InfrastructureResponse.RESULT_WARNING, response.getResult());
		Assert.assertFalse("ErrorInfo expected", response.getErrors().isEmpty());
	}
	
	@Test
	public void testDeliveryFailureIsSentWhenPayloadPublishingFailsWithAnException() throws Exception {
		final boolean isInfAck = false;
		final DistributionEnvelope envelope = createExampleRequest(isInfAck);
		envelope.getHandlingSpec().setInfrastructureAckRequested(true);
		
		payloadReceiver.whenAnyExchangeReceived(new Processor() {
			@Override
			public void process(final Exchange exchange) throws Exception {
				throw new Exception("Simulating message delivery failure");
			}
		});
		
		sendDistributionEnvelope(envelope);
		
		// async nack should be sent (after payload message publishing is attempted)
		infrastructureResponseReceiver.expectedMessageCount(1);
		
		infrastructureResponseReceiver.assertIsSatisfied(1000);
		
		// Check that the response was a nack
		final DistributionEnvelope nack = getFirstInfrastructureResponse();
		Assert.assertTrue("isInfrastructureAck", nack.getHandlingSpec().isInfrastructureAck());
		Assert.assertEquals("single payload", 1, nack.getPayloads().size());
		
		final InfrastructureResponse response = toInfrastructureResponse(nack.getPayloads().get(0).getBody());
		Assert.assertEquals("Warning response", InfrastructureResponse.RESULT_WARNING, response.getResult());
		Assert.assertFalse("ErrorInfo expected", response.getErrors().isEmpty());
	}
	
	@Test
	public void testDeliveryFailureIsSentWhenPayloadPublishingFailsWithAFaultMessage() throws Exception {
		final boolean isInfAck = false;
		final DistributionEnvelope envelope = createExampleRequest(isInfAck);
		envelope.getHandlingSpec().setInfrastructureAckRequested(true);
		
		payloadReceiver.whenAnyExchangeReceived(new Processor() {
			@Override
			public void process(final Exchange exchange) throws Exception {
				exchange.getOut().setBody("Simulating message delivery failure");
				exchange.getOut().setFault(true);
			}
		});
		
		sendDistributionEnvelope(envelope);
				
		// async nack should be sent (after payload message publishing is attempted)
		infrastructureResponseReceiver.expectedMessageCount(1);
		
		infrastructureResponseReceiver.assertIsSatisfied(1000);
		
		// Check that the response was a nack
		final DistributionEnvelope nack = getFirstInfrastructureResponse();
		Assert.assertTrue("isInfrastructureAck", nack.getHandlingSpec().isInfrastructureAck());
		Assert.assertEquals("single payload", 1, nack.getPayloads().size());
		
		final InfrastructureResponse response = toInfrastructureResponse(nack.getPayloads().get(0).getBody());
		Assert.assertEquals("Warning response", InfrastructureResponse.RESULT_WARNING, response.getResult());
		Assert.assertFalse("ErrorInfo expected", response.getErrors().isEmpty());
	}
	
	private DistributionEnvelope createExampleRequest(final boolean isInfAck) throws Exception {
		final DistributionEnvelope envelope = new DistributionEnvelope();

		envelope.setAuditIdentity("audit-identity");
		envelope.setSenderAddress("the-sender");
		envelope.addAddress(new Address("the-target"));
		envelope.setService("service-id");

		if (isInfAck) {
			// For this test - the content of the payload is not too important
			final InfrastructureResponse ack = new InfrastructureResponse();
			ack.setResult(InfrastructureResponse.RESULT_OK);
			
			final ManifestItem manifestItem = new ManifestItem();
			manifestItem.setMimeType("text/xml");
			envelope.addPayload(manifestItem, serialize(ack));
			envelope.getHandlingSpec().setInfrastructureAck(true);
		} else {
			final String body = "<root>payload</root>";
			
			final ManifestItem manifestItem = new ManifestItem();
			manifestItem.setMimeType("text/xml");
			envelope.addPayload(manifestItem, body);
			envelope.getHandlingSpec().setBusinessAck(true);
		}
		
		envelope.applyDefaults();
		
		return envelope;
	}
	
	private String serialize(final InfrastructureResponse ack) {
		return context.getTypeConverter().convertTo(String.class, ack);
	}
	
	private InfrastructureResponse toInfrastructureResponse(final String body) {
		try {
			return context.getTypeConverter().mandatoryConvertTo(InfrastructureResponse.class, body);
		} catch (Exception e) {
			throw Throwables.propagate(e);
		}
	}
	
	private void expectSinglePayload(final DistributionEnvelope envelope) throws Exception {
		payloadReceiver.expectedMessageCount(1);
		final String payloadBody = getPayloadBody(envelope);
		payloadReceiver.expectedMessagesMatches(new Predicate() {
			@Override
			public boolean matches(final Exchange exchange) {
				final DistributionEnvelope received = exchange.getIn().getBody(DistributionEnvelope.class);
				Assert.assertNotNull("Expected DistributionEnvelope", received);
				Assert.assertEquals("Expected 1 payload", received.getPayloads().size(), 1);
				Assert.assertEquals("Expected payload body", payloadBody, received.getPayloads().get(0).getBody());
				
				return true;
			}
		});
	}
	
	private String getPayloadBody(final DistributionEnvelope envelope) throws Exception {
		return envelope.getPayloads().get(0).getBody();
	}
	
	private DistributionEnvelope getFirstInfrastructureResponse() throws InvalidPayloadException {
		return infrastructureResponseReceiver.getExchanges().get(0).getIn().getMandatoryBody(DistributionEnvelope.class);
	}
	
	private void sendDistributionEnvelope(final DistributionEnvelope envelope) throws Exception {
		final Exchange exchange = new DefaultExchange(context);
		exchange.getIn().setBody(envelope, String.class); // convert the body

		
		sendDistributionEnvelope(exchange);
	}
	
	private void sendDistributionEnvelope(final Exchange exchange) throws Exception {
		exchange.setPattern(ExchangePattern.InOnly);
		
		producerTemplate.send("direct:distribution-envelope-receiver", exchange);
		if (exchange.getException() != null) {
			throw exchange.getException();
		} else if (exchange.getOut().isFault()) {
			throw new Exception(exchange.getIn().getMandatoryBody(String.class));
		}
		
		// no fault
	}
}
