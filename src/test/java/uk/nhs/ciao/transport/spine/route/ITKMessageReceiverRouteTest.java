package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;

import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope;
import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope.Address;
import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope.ManifestItem;
import uk.nhs.ciao.transport.spine.itk.Identity;
import uk.nhs.ciao.transport.spine.itk.InfrastructureResponse;

public class ITKMessageReceiverRouteTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(ITKMessageReceiverRouteTest.class);
	
	private CamelContext context;
	private ProducerTemplate producerTemplate;
	
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
		
		context.addRoutes(new ITKMessageReceiverRoute());
		
		context.start();
		producerTemplate.start();
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
	public void testInfrastructureAck() throws Exception {
		final Identity identity = new Identity("urn:someone");
		
		final InfrastructureResponse response = new InfrastructureResponse();
		response.setReportingIdentity(identity);
		response.setTrackingIdRef("12345");
		response.setServiceRef("someservice");
		response.setTimestamp("2015-08-03T12:25:32Z");
		response.setResult(InfrastructureResponse.RESULT_OK);
		
		final DistributionEnvelope envelope = new DistributionEnvelope();
		envelope.setAuditIdentity(identity);
		envelope.setSenderAddress(identity.getUri());
		envelope.setService(response.getServiceRef());
		envelope.getAddresses().add(new Address("urn:sometarget"));
		envelope.getHandlingSpec().setInfrastructureAck(true);
		
		final ManifestItem manifestItem = new ManifestItem();
		manifestItem.setMimeType("text/xml");
		envelope.addPayload(manifestItem, serialize(response));
		envelope.applyDefaults();
		
		sendDistributionEnvelope(envelope);
	}
	
	private String serialize(final InfrastructureResponse ack) {
		return context.getTypeConverter().convertTo(String.class, ack);
	}
	
	private void sendDistributionEnvelope(final DistributionEnvelope envelope) throws Exception {
		final Exchange exchange = new DefaultExchange(context);
		exchange.getIn().setBody(envelope, String.class); // convert the body

		
		sendDistributionEnvelope(exchange);
	}
	
	private void sendDistributionEnvelope(final Exchange exchange) throws Exception {
		exchange.setPattern(ExchangePattern.InOnly);
		
		producerTemplate.send("direct:itk-message-receiver", exchange);
		if (exchange.getException() != null) {
			throw exchange.getException();
		} else if (exchange.getOut().isFault()) {
			throw new Exception(exchange.getIn().getMandatoryBody(String.class));
		}
		
		// no fault
	}
}
