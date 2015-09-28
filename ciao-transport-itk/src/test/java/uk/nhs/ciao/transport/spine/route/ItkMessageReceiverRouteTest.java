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
import org.springframework.transaction.PlatformTransactionManager;

import uk.nhs.ciao.camel.CamelUtils;
import uk.nhs.ciao.transport.spine.itk.Address;
import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope;
import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope.ManifestItem;
import uk.nhs.ciao.transport.spine.itk.Identity;
import uk.nhs.ciao.transport.spine.itk.InfrastructureResponse;
import uk.nhs.ciao.transport.spine.itk.InfrastructureResponse.ErrorInfo;
import uk.nhs.ciao.docs.parser.route.InProgressFolderManagerRoute.*;

public class ItkMessageReceiverRouteTest {
	private CamelContext context;
	private ProducerTemplate producerTemplate;
	
	private MockEndpoint inProgressFolderManager;
	
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
		
		final ItkMessageReceiverRoute route = new ItkMessageReceiverRoute();
		route.setItkMessageReceiverUri("direct:itk-message-receiver");
		route.setInProgressFolderManagerUri("mock:in-progress-folder-manager");
		context.addRoutes(route);
		
		context.start();
		producerTemplate.start();
		
		inProgressFolderManager = MockEndpoint.resolve(context, "mock:in-progress-folder-manager");
	}
	
	@After
	public void teardown() {
		CamelUtils.stopQuietly(producerTemplate, context);
	}
	
	@Test
	public void testInfrastructureAck() throws Exception {
		final InfrastructureResponse response = createInfrastructureResponse(InfrastructureResponse.RESULT_OK);
		final DistributionEnvelope envelope = createDistributionEnvelope(response);

		inProgressFolderManager.expectedHeaderReceived(Exchange.FILE_NAME, "12345/inf-ack");
		
		inProgressFolderManager.expectedHeaderReceived(Exchange.CORRELATION_ID, "12345");
		inProgressFolderManager.expectedHeaderReceived(Header.ACTION, Action.STORE);
		inProgressFolderManager.expectedHeaderReceived(Header.EVENT_TYPE, EventType.MESSAGE_RECEIVED);
		inProgressFolderManager.expectedHeaderReceived(Exchange.FILE_NAME, "inf-ack");
		
		sendDistributionEnvelope(envelope);
		
		inProgressFolderManager.assertIsSatisfied();
	}
	
	@Test
	public void testInfrastructureNack() throws Exception {
		final InfrastructureResponse response = createInfrastructureResponse(InfrastructureResponse.RESULT_FAILURE);
		final ErrorInfo errorInfo = new ErrorInfo();
		errorInfo.setId("errorid");
		errorInfo.setText("error text");
		response.getErrors().add(errorInfo);
		final DistributionEnvelope envelope = createDistributionEnvelope(response);

		inProgressFolderManager.expectedHeaderReceived(Exchange.CORRELATION_ID, "12345");
		inProgressFolderManager.expectedHeaderReceived(Header.ACTION, Action.STORE);
		inProgressFolderManager.expectedHeaderReceived(Header.EVENT_TYPE, EventType.MESSAGE_RECEIVED);
		inProgressFolderManager.expectedHeaderReceived(Exchange.FILE_NAME, "inf-nack");
		
		sendDistributionEnvelope(envelope);
		
		inProgressFolderManager.assertIsSatisfied();
	}
	
	@Test
	public void testBusinessAck() throws Exception {
		final DistributionEnvelope envelope = context.getTypeConverter().mandatoryConvertTo(DistributionEnvelope.class,
				getClass().getResourceAsStream("test-wrapped-busack.xml"));
		envelope.getHandlingSpec().setBusinessAck(true);

		inProgressFolderManager.expectedMessageCount(1);
		inProgressFolderManager.expectedHeaderReceived(Exchange.CORRELATION_ID, "7D6F23E0-AE1A-11DB-8707-B18E1E0994EF");
		inProgressFolderManager.expectedHeaderReceived(Header.ACTION, Action.STORE);
		inProgressFolderManager.expectedHeaderReceived(Header.EVENT_TYPE, EventType.MESSAGE_RECEIVED);
		inProgressFolderManager.expectedHeaderReceived(Exchange.FILE_NAME, "bus-ack");
		
		sendDistributionEnvelope(envelope);
		
		inProgressFolderManager.assertIsSatisfied();
	}
	
	@Test
	public void testBusinessNack() throws Exception {
		final DistributionEnvelope envelope = context.getTypeConverter().mandatoryConvertTo(DistributionEnvelope.class,
				getClass().getResourceAsStream("test-wrapped-busnack.xml"));
		envelope.getHandlingSpec().setBusinessAck(true);

		inProgressFolderManager.expectedMessageCount(1);
		inProgressFolderManager.expectedHeaderReceived(Exchange.CORRELATION_ID, "7D6F23E0-AE1A-11DB-8707-B18E1E0994EF");
		inProgressFolderManager.expectedHeaderReceived(Header.ACTION, Action.STORE);
		inProgressFolderManager.expectedHeaderReceived(Header.EVENT_TYPE, EventType.MESSAGE_RECEIVED);
		inProgressFolderManager.expectedHeaderReceived(Exchange.FILE_NAME, "bus-nack");
		
		sendDistributionEnvelope(envelope);
		
		inProgressFolderManager.assertIsSatisfied();
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
