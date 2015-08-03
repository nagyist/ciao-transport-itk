package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Predicate;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;

import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope;
import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope.Address;
import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope.ManifestItem;
import uk.nhs.ciao.transport.spine.itk.InfrastructureResponse;

public class DistributionEnvelopeMessageReceiverRouteTest {
private static final Logger LOGGER = LoggerFactory.getLogger(DistributionEnvelopeMessageReceiverRouteTest.class);
	
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
		propegationRequiresNew.setTransactionManager(Mockito.mock(PlatformTransactionManager.class));
		propegationRequiresNew.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
		registry.put("PROPAGATION_REQUIRES_NEW", propegationRequiresNew);
		
		context.addRoutes(new DistributionEnvelopeReceiverRoute());
		
		context.start();
		producerTemplate.start();
		
		payloadReceiver = MockEndpoint.resolve(context, "mock:distribution-envelope-payloads");
		infrastructureResponseReceiver = MockEndpoint.resolve(context, "mock:infrastructure-responses");
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
		exchange.setPattern(ExchangePattern.InOut);
		
		producerTemplate.send("direct:distribution-envelope-receiver", exchange);
		if (exchange.getException() != null) {
			throw exchange.getException();
		} else if (exchange.getOut().isFault()) {
			throw new Exception(exchange.getOut().getMandatoryBody(String.class));
		}
		
		// no fault
	}
}
