package uk.nhs.ciao.transport.spine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import uk.nhs.ciao.camel.CamelApplicationRunner;
import uk.nhs.ciao.camel.CamelUtils;
import uk.nhs.ciao.camel.CamelApplicationRunner.AsyncExecution;
import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.configuration.impl.MemoryCipProperties;
import uk.nhs.ciao.docs.parser.Document;
import uk.nhs.ciao.docs.parser.HeaderNames;
import uk.nhs.ciao.docs.parser.ParsedDocument;
import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope;
import uk.nhs.ciao.transport.spine.multipart.MultipartBody;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;


/**
 * Tests for the cda-builder-parser CIP application
 */
public class SpineTransportApplicationTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(SpineTransportApplicationTest.class);
	private static final String CIP_NAME = "ciao-transport-spine";
	
	@Rule
	public Timeout globalTimeout = Timeout.seconds(30);
	
	private ExecutorService executorService;
	private SpineTransportApplication application;
	private AsyncExecution execution;
	private ObjectMapper objectMapper;
	private ProducerTemplate producerTemplate;
	
	@Before
	public void setup() throws Exception {
		final CIAOConfig ciaoConfig = setupCiaoConfig();
		application = new SpineTransportApplication(ciaoConfig);
		
		executorService = Executors.newSingleThreadExecutor();
		objectMapper = new ObjectMapper();
	}
	
	private CIAOConfig setupCiaoConfig() throws IOException {
		final MemoryCipProperties cipProperties = new MemoryCipProperties(CIP_NAME, "tests");
		addProperties(cipProperties, CIP_NAME + ".properties");
		addProperties(cipProperties, CIP_NAME + "-test.properties");
		
		return new CIAOConfig(cipProperties);
	}
	
	private void addProperties(final MemoryCipProperties cipProperties, final String resourcePath) throws IOException {
		final Resource resource = new ClassPathResource(resourcePath);
		final Properties properties = PropertiesLoaderUtils.loadProperties(resource);
		cipProperties.addConfigValues(properties);
	}
	
	private void runApplication() throws Exception {
		LOGGER.info("About to start camel application");
		
		execution = CamelApplicationRunner.runApplication(application, executorService);
		
		LOGGER.info("Camel application has started");
	}
	
	@After
	public void tearDown() {
		try {
			stopApplication();
		} finally {
			// Always stop the executor service
			executorService.shutdownNow();
		}
	}
	
	private void stopApplication() {
		if (execution == null) {
			return;
		}
		
		CamelUtils.stopQuietly(producerTemplate);
		
		final CamelContext context = getCamelContext();
		try {
			LOGGER.info("About to stop camel application");
			execution.getRunner().stop();
			execution.getFuture().get(); // wait for task to complete
			LOGGER.info("Camel application has stopped");
		} catch (Exception e) {
			LOGGER.warn("Exception while trying to stop camel application", e);
		} finally {
			if (context != null) {
				MockEndpoint.resetMocks(context);
			}
		}
	}
	
	private CamelContext getCamelContext() {
		if (execution == null) {
			return null;
		}
		
		final List<CamelContext> camelContexts = execution.getRunner().getCamelContexts();
		return camelContexts.isEmpty() ? null : camelContexts.get(0);
	}
	
	private ProducerTemplate getProducerTemplate() throws Exception {
		if (producerTemplate == null) {
			producerTemplate = new DefaultProducerTemplate(getCamelContext());
			producerTemplate.start();
		}
		
		return producerTemplate;
	}
	
	@Test
	public void testApplicationStartsUsingSpringConfig() throws Exception {
		LOGGER.info("Checking the application starts via spring config");

		runApplication();
		
		assertNotNull(execution);
		assertFalse(execution.getRunner().getCamelContexts().isEmpty());
		assertNotNull(getCamelContext());
	}
	
	@Test
	public void testApplicationProcessesAJsonDocument() throws Exception {
		LOGGER.info("Checking a parsable document");

		runApplication();
		
		// Using latch to wait for response - using a mock with assertions does not seem to work as expected
		final CountDownLatch latch = new CountDownLatch(1);
		
		final CamelContext camelContext = getCamelContext();
		sendRouteTo("trunk-request-sender", "direct:trunk-responses");
		
		camelContext.addRoutes(new RouteBuilder() {			
			@Override
			public void configure() throws Exception {
				// Send an ack whenever a request is received
				from("seda:trunk-request")
				.convertBodyTo(MultipartBody.class)
				.setBody().spel("#{body.parts[0].body}")
				.convertBodyTo(EbxmlEnvelope.class)
				.setBody().spel("#{body.generateAcknowledgment()}")
				.setHeader("JMSCorrelationID").simple("${body.messageData.refToMessageId}", String.class)
				.setHeader(Exchange.CORRELATION_ID).header("JMSCorrelationID")
				.convertBodyTo(String.class)
				.setHeader("SOAPAction", constant("urn:oasis:names:tc:ebxml-msg:service/Acknowledgment"))
				.to("direct:trunk-reply");
				
				// Monitor the responses and unlock the latch
				from("direct:trunk-responses")
					.log("Got an ack response message: ${body}")
					.process(new Processor() {
						@Override
						public void process(final Exchange exchange) throws Exception {
							latch.countDown();
						}
					});
			}
		});
		
		// Send the initial document to JMS (returns before main processing begins)
		sendMessage("jms:queue:cda-documents", getExampleJson());
		
		// Wait for ACK response to be processed by the main route
		Assert.assertTrue("Expected one response message", latch.await(10, TimeUnit.SECONDS));
	}
	
	/**
	 * Sends the end of the route to a further endpoint for testing (e.g. direct:...)
	 * @throws Exception 
	 */
	@SuppressWarnings("deprecation")
	private void sendRouteTo(final String routeId, final String toUri) throws Exception {
		// A little 'hacky' - 
		// This version of camel does not support the failIfNoConsumers option on the direct component
		// otherwise a NOOP route could have been added in the main route
		// without the option the route would pass unit tests - but fail when running normally!
		final CamelContext camelContext = getCamelContext();
		
		final Route route = camelContext.getRoute(routeId);
		Assert.assertNotNull(route);
		
		camelContext.stopRoute(routeId);
		camelContext.removeRoute(routeId);		
		camelContext.addRouteDefinition(route.getRouteContext().getRoute().to(toUri));
		camelContext.startRoute(routeId);
	}

	private Exchange sendMessage(final String uri, final Message message) throws Exception {
		final Exchange exchange = getCamelContext().getEndpoint(uri).createExchange();
		exchange.getIn().copyFrom(message);
		
		return getProducerTemplate().send(uri, exchange);
	}
	
	private Message getExampleJson() throws IOException {
		final Map<String, Object> properties = Maps.newLinkedHashMap();
		properties.put("somekey", "somevalue");
		properties.put("itkCorrelationId", "12345");
		properties.put("ebxmlCorrelationId", "89EEFD54-7C9E-4B6F-93A8-835CFE6EFC95");
		
		properties.put("receiverPartyId", "BBB-654321");
		properties.put("receiverAsid", "000000000000");
		properties.put("receiverODSCode", "BBB");
		properties.put("receiverCPAId", "S3024519A3110234");
		properties.put("itkProfileId", "urn:nhs-en:profile:eDischargeInpatientDischargeSummary-v1-0");
		properties.put("itkHandlingSpec", "urn:nhs-itk:interaction:copyRecipientAmbulanceServicePatientReport-v1-0");
		
		final Document originalDocument = new Document("myfile.xml", "<root>content</root>".getBytes(), "text/xml");
		final ParsedDocument parsedDocument = new ParsedDocument(originalDocument, properties);
		
		final Message message = new DefaultMessage();
		message.setBody(objectMapper.writeValueAsString(parsedDocument));
		message.setHeader(Exchange.CORRELATION_ID, "12345");
		
		return message;
	}
}
