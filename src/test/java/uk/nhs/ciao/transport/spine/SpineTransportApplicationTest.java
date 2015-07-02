package uk.nhs.ciao.transport.spine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.After;
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
import uk.nhs.ciao.camel.CamelApplicationRunner.AsyncExecution;
import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.configuration.impl.MemoryCipProperties;
import uk.nhs.ciao.docs.parser.Document;
import uk.nhs.ciao.docs.parser.ParsedDocument;

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
		
		// Route the output to a mock
		final CamelContext camelContext = getCamelContext();
		camelContext.addRoutes(new RouteBuilder() {			
			@Override
			public void configure() throws Exception {
				from("jms:queue:trunk-requests")
				.to("mock:output");
			}
		});
		
		final Producer producer = camelContext.getEndpoint("jms:queue:documents")
				.createProducer();
		final MockEndpoint endpoint = MockEndpoint.resolve(camelContext, "mock:output");
		endpoint.expectedMessageCount(1);
		endpoint.expectedMessagesMatches(new Predicate() {			
			@Override
			public boolean matches(final Exchange exchange) {
				// For now just check that we get a text body out
				final String multipartBody = exchange.getIn().getBody(String.class);
				assertNotNull("multipartBody", multipartBody);
				assertFalse("multipartBody should not be empty", multipartBody.isEmpty());
				
				LOGGER.info("Multipart body:\n" + multipartBody);
				
				return true;
			}
		});
		
		sendMessage(producer, getExampleJson());
		
		MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, endpoint);
	}

	private void sendMessage(final Producer producer, final Object body) throws Exception {
		final Exchange exchange = producer.createExchange();
		final Message message = exchange.getIn();
		message.setBody(body);
		producer.process(exchange);
	}
	
	private String getExampleJson() throws IOException {
		final Map<String, Object> properties = Maps.newLinkedHashMap();
		properties.put("somekey", "somevalue");
		properties.put("itkCorrelationId", "12345");
		
		final Document originalDocument = new Document("myfile.xml", "<root>content</root>".getBytes(), "text/xml");
		final ParsedDocument parsedDocument = new ParsedDocument(originalDocument, properties);
		return objectMapper.writeValueAsString(parsedDocument);
	}
}
