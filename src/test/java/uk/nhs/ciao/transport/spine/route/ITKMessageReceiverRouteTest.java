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
import uk.nhs.ciao.transport.spine.itk.InfrastructureResponse.ErrorInfo;

public class ITKMessageReceiverRouteTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(ITKMessageReceiverRouteTest.class);
	
	private CamelContext context;
	private ProducerTemplate producerTemplate;
	
	private MockEndpoint inProgressDirectory;
	
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
		
		final ITKMessageReceiverRoute route = new ITKMessageReceiverRoute();
		route.setItkMessageReceiverUri("direct:itk-message-receiver");
		route.setInProgressDirectoryUri("mock:in-progress-directory");
		context.addRoutes(route);
		
		context.start();
		producerTemplate.start();
		
		inProgressDirectory = MockEndpoint.resolve(context, "mock:in-progress-directory");
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
		final InfrastructureResponse response = createInfrastructureResponse(InfrastructureResponse.RESULT_OK);
		final DistributionEnvelope envelope = createDistributionEnvelope(response);

		inProgressDirectory.expectedHeaderReceived(Exchange.FILE_NAME, "12345/inf-ack");
		
		sendDistributionEnvelope(envelope);
		
		inProgressDirectory.assertIsSatisfied();
	}
	
	@Test
	public void testInfrastructureNack() throws Exception {
		final InfrastructureResponse response = createInfrastructureResponse(InfrastructureResponse.RESULT_FAILURE);
		final ErrorInfo errorInfo = new ErrorInfo();
		errorInfo.setId("errorid");
		errorInfo.setText("error text");
		response.getErrors().add(errorInfo);
		final DistributionEnvelope envelope = createDistributionEnvelope(response);

		inProgressDirectory.expectedHeaderReceived(Exchange.FILE_NAME, "12345/inf-nack");
		
		sendDistributionEnvelope(envelope);
		
		inProgressDirectory.assertIsSatisfied();
	}
	
	@Test
	public void testBusinessAck() throws Exception {
		final DistributionEnvelope envelope = context.getTypeConverter().mandatoryConvertTo(DistributionEnvelope.class,
				getClass().getResourceAsStream("test-wrapped-busack.xml"));
		envelope.getHandlingSpec().setBusinessAck(true);

		inProgressDirectory.expectedMessageCount(1);
		inProgressDirectory.expectedHeaderReceived(Exchange.FILE_NAME, "7D6F23E0-AE1A-11DB-8707-B18E1E0994EF/bus-ack");
		
		sendDistributionEnvelope(envelope);
		
		inProgressDirectory.assertIsSatisfied();
	}
	
	@Test
	public void testBusinessNack() throws Exception {
		final DistributionEnvelope envelope = context.getTypeConverter().mandatoryConvertTo(DistributionEnvelope.class,
				getClass().getResourceAsStream("test-wrapped-busnack.xml"));
		envelope.getHandlingSpec().setBusinessAck(true);

		inProgressDirectory.expectedMessageCount(1);
		inProgressDirectory.expectedHeaderReceived(Exchange.FILE_NAME, "7D6F23E0-AE1A-11DB-8707-B18E1E0994EF/bus-nack");
		
		sendDistributionEnvelope(envelope);
		
		inProgressDirectory.assertIsSatisfied();
	}
	
	private InfrastructureResponse createInfrastructureResponse(final String result) {
		final InfrastructureResponse response = new InfrastructureResponse();

		response.setReportingIdentity(new Identity("urn:someone"));
		response.setTrackingIdRef("12345");
		response.setServiceRef("someservice");
		response.setTimestamp("2015-08-03T12:25:32Z");
		response.setResult(result);
		
		return response;
	}
	
	private DistributionEnvelope createDistributionEnvelope(final InfrastructureResponse response) {		
		final DistributionEnvelope envelope = new DistributionEnvelope();

		envelope.setAuditIdentity(new Identity(response.getReportingIdentity()));
		envelope.setSenderAddress(response.getReportingIdentity().getUri());
		envelope.setService(response.getServiceRef());
		envelope.getAddresses().add(new Address("urn:sometarget"));
		envelope.getHandlingSpec().setInfrastructureAck(true);
		
		final ManifestItem manifestItem = new ManifestItem();
		manifestItem.setMimeType("text/xml");
		envelope.addPayload(manifestItem, serialize(response));
		envelope.applyDefaults();
		
		return envelope;
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
