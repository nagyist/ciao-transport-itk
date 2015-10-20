package uk.nhs.ciao.transport.dts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;
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
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.util.FileUtil;
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

import uk.nhs.ciao.camel.CamelApplication;
import uk.nhs.ciao.camel.CamelApplicationRunner;
import uk.nhs.ciao.camel.CamelApplicationRunner.AsyncExecution;
import uk.nhs.ciao.camel.CamelUtils;
import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.configuration.impl.MemoryCipProperties;
import uk.nhs.ciao.docs.parser.Document;
import uk.nhs.ciao.docs.parser.ParsedDocument;
import uk.nhs.ciao.docs.parser.route.InProgressFolderManagerRoute.EventType;
import uk.nhs.ciao.docs.parser.route.InProgressFolderManagerRoute.Header;
import uk.nhs.ciao.dts.ControlFile;
import uk.nhs.ciao.dts.Event;
import uk.nhs.ciao.dts.Status;
import uk.nhs.ciao.dts.StatusRecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Tests for the ciao-transport-dts CIP application
 */
public class DTSTransportApplicationTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(DTSTransportApplication.class);
	private static final String CIP_NAME = "ciao-transport-dts";
	
	@Rule
	public Timeout globalTimeout = Timeout.seconds(30);
	
	private ExecutorService executorService;
	private DTSTransportApplication application;
	private AsyncExecution execution;
	private ObjectMapper objectMapper;
	private ProducerTemplate producerTemplate;
	
	@Before
	public void setup() throws Exception {
		final CIAOConfig ciaoConfig = setupCiaoConfig();
		cleanDirectories(getDTSRootFolder(ciaoConfig));
		application = new DTSTransportApplication(ciaoConfig);
		
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
		final File dtsRootFolder = getDTSRootFolder();
		try {
			stopApplication();
		} finally {
			// Always stop the executor service
			executorService.shutdownNow();
			
			cleanDirectories(dtsRootFolder);
		}
	}
	
	private File getDTSRootFolder() {
		try {
			final CIAOConfig config = CamelApplication.getConfig(getCamelContext());
			return getDTSRootFolder(config);
		} catch (Exception e) {
			LOGGER.warn("Unable to find the DTS root folder", e);
			return null;
		}
	}
	
	private File getDTSRootFolder(final CIAOConfig config) {
		try {
			return config == null ? null : new File(config.getConfigValue("dts.rootFolder"));
		} catch (Exception e) {
			LOGGER.warn("Unable to find the DTS root folder", e);
			return null;
		}
	}
	
	private void cleanDirectories(final File rootFolder) {
		try {
			if (rootFolder != null && rootFolder.isDirectory()) {
				FileUtil.removeDir(rootFolder);
			}
		} catch (Exception e) {
			LOGGER.warn("Unable to clean directories", e);
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
	
	@SuppressWarnings("deprecation")
	@Test
	public void testApplicationProcessesAJsonDocument() throws Exception {
		LOGGER.info("Checking a parsable document");

		runApplication();
		
		final CamelContext context = getCamelContext();
		
		// reroute the output for the folder manager into a mock for testing
		
		for (final Route route: Lists.newArrayList(context.getRoutes())) {
			if (route.getEndpoint().getEndpointUri().endsWith("in-progress-folder-manager")) {
				context.stopRoute(route.getId());
				context.removeRoute(route.getId());
				context.addRouteDefinition(route.getRouteContext().getRoute()
						.to("direct:rerouted-in-progress-folder-manager"));
				context.startRoute(route.getId());
				break;
			}
		}
		
		context.addRoutes(new RouteBuilder() {			
			@Override
			public void configure() throws Exception {
				// Send an ack whenever a request is received
				from(context.resolvePropertyPlaceholders("file://{{dts.rootFolder}}/OUT?delete=true"))
					.choice()
						.when(PredicateBuilder.endsWith(header(Exchange.FILE_NAME), constant(".ctl")))
							.log("***** Creating success response for ${header.CamelFileName}")
							.bean(new SuccessResponseBuilder())
							.to(context.resolvePropertyPlaceholders("file://{{dts.rootFolder}}/SENT?tempPrefix=../sent-temp"))
						.endChoice()
					.end()
				.end();
				
				from("direct:rerouted-in-progress-folder-manager")
					.filter(header(Header.EVENT_TYPE))
					.log("***** Received event message from the InProgressFolderManager")
					.to("mock:in-progress-folder-manager");
			}
		});
		
		final MockEndpoint inProgressFileManager = MockEndpoint.resolve(context, "mock:in-progress-folder-manager");
		inProgressFileManager.expectedMessageCount(2);
		
		// Send the initial document to JMS (returns before main processing begins)
		sendMessage("jms:queue:cda-documents", getExampleJson());
		
		// Wait for ACK response to be processed by the main route
		MockEndpoint.assertIsSatisfied(5, TimeUnit.SECONDS, inProgressFileManager);
		
		final Message sendNotification = inProgressFileManager.getExchanges().get(1).getIn();
		Assert.assertEquals(EventType.MESSAGE_SENT, sendNotification.getHeader(Header.EVENT_TYPE));
		
		final ControlFile controlFile = sendNotification.getMandatoryBody(ControlFile.class);
		Assert.assertEquals(controlFile.getLocalId(), sendNotification.getHeader(Exchange.CORRELATION_ID));
	}
	
	public static class SuccessResponseBuilder {
		public ControlFile createResponse(final ControlFile controlFile) {
			final ControlFile response = new ControlFile();
			response.copyFrom(controlFile, true);
			
			final StatusRecord statusRecord = new StatusRecord();
			statusRecord.setEvent(Event.TRANSFER);
			statusRecord.setStatus(Status.SUCCESS);
			statusRecord.setStatusCode("00");			
			response.setStatusRecord(statusRecord);
			
			response.applyDefaults();
			return response;
		}
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
