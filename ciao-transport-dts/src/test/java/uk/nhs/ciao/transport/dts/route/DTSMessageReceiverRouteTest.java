package uk.nhs.ciao.transport.dts.route;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.collect.Maps;

import uk.nhs.ciao.camel.CamelUtils;
import uk.nhs.ciao.dts.AddressType;
import uk.nhs.ciao.dts.ControlFile;
import uk.nhs.ciao.dts.Event;
import uk.nhs.ciao.dts.MessageType;
import uk.nhs.ciao.dts.Status;
import uk.nhs.ciao.dts.StatusRecord;
import uk.nhs.ciao.transport.dts.processor.DTSDataFilePoller;

/**
 * Unit tests for {@link DTSMessageReceiverRoute}
 */
public class DTSMessageReceiverRouteTest {
	private SimpleRegistry registry;
	private CamelContext context;
	private ProducerTemplate producerTemplate;
	private DTSMessageReceiverRoute route;
	private MockEndpoint payloadDestination;
	private DTSDataFilePoller filePoller;
	private File inputFolder;
	private File errorFolder;
	private Map<File, Object> dataFiles;
	
	private File controlFile;
	private File dataFile;
	
	@Before
	public void setup() throws Exception {
		registry = new SimpleRegistry();
		context = new DefaultCamelContext(registry);
		
		producerTemplate = new DefaultProducerTemplate(context);
		setupRoute();
		
		context.start();
		producerTemplate.start();
		
		Mockito.reset(route);
	}
	
	@After
	public void tearDown() throws Exception {
		CamelUtils.stopQuietly(producerTemplate, context);
	}
	
	private void setupRoute() throws Exception {
		dataFiles = Maps.newLinkedHashMap();
		
		// disable async retries for unit tests
		filePoller = new DTSDataFilePoller(Mockito.mock(ScheduledExecutorService.class), 0, 1) {
			@Override
			protected boolean fileExists(final File file) {
				return dataFiles.containsKey(file);
			}
			
			@Override
			protected void addDataFileToMessage(final Message message, final File file) {
				message.setHeader(DTSDataFilePoller.HEADER_DATA_FILE, dataFiles.get(file));
			}
		};
		
		// stub out direct interactions with the file system
		route = Mockito.spy(new DTSMessageReceiverRoute() {
			@Override
			protected void moveFile(final File source, final File destination) throws IOException {
				// NOOP
			}
			
			@Override
			protected void deleteFile(final File file) {
				// NOOP
			}
			
			@Override
			protected DTSDataFilePoller createDataFilePoller(final ScheduledExecutorService executorService,
					final long pollingInterval, final int maxAttempts) {
				return filePoller;
			}
		});
		
		route.setDTSMessageReceiverUri("direct:dts-message-receiver");
		route.setPayloadDestinationUri("mock:payload-destination");
		route.setDTSErrorFolder("error");
		
		// add file router
		final DTSIncomingFileRouterRoute router = new DTSIncomingFileRouterRoute();
		
		registry.put("dtsInIdempotentRepository", new MemoryIdempotentRepository());
		router.setInIdempotentRepositoryId("dtsInIdempotentRepository");
		
		registry.put("dtsInInProgressRepository", new MemoryIdempotentRepository());
		router.setInInProgressRepositoryId("dtsInInProgressRepository");
		
		registry.put("dtsSentIdempotentRepository", new MemoryIdempotentRepository());
		router.setSentIdempotentRepositoryId("dtsSentIdempotentRepository");
		
		registry.put("dtsSentInProgressRepository", new MemoryIdempotentRepository());
		router.setSentInProgressRepositoryId("dtsSentInProgressRepository");
		
		router.setDTSInUri("stub:file://./target/example");
		router.setDTSMessageSendNotificationReceiverUri("mock:send-notification-receiver");
		router.setDTSSentUri("stub:send:send-notification-sender");
		router.setDTSMessageReceiverUri("direct:dts-message-receiver");
		router.setMailboxes(Arrays.asList("to-dts"));
		router.setWorkflowIds(Arrays.asList("workflow-1", "workflow-2"));
		context.addRoutes(router);
		
		inputFolder = new File("./target/example");
		errorFolder = new File(inputFolder, "error");
		
		payloadDestination = MockEndpoint.resolve(context, "mock:payload-destination");
		payloadDestination.setSynchronous(true);
		
		context.addRoutes(route);
	}
	
	@Test
	public void testDataFileIsPublished() throws Exception {
		final String id = "1234";
		
		final ControlFile control = createTransferControlFile(id);
		control.setWorkflowId("workflow-1");
		control.getStatusRecord().setStatus(Status.SUCCESS);
		control.getStatusRecord().setStatusCode("00");
		
		// expectations
		payloadDestination.expectedBodiesReceived("payload contents");
		
		// publish the control file
		sendControlFile(id, control, "payload contents");
		
		// verification
		payloadDestination.assertIsSatisfied();
		assertControlFileHeadersWerePropagated("workflow-1");
		assertFilesWereDeleted();
	}
	
	@Test
	public void testControlFileWithUnsupportedWorkflowIdIsIgnored() throws Exception {
		final String id = "1234";
		
		final ControlFile control = createTransferControlFile(id);
		control.setWorkflowId("unsupported");
		control.getStatusRecord().setStatus(Status.SUCCESS);
		control.getStatusRecord().setStatusCode("00");
		
		// expectations
		payloadDestination.expectedMessageCount(0);
		
		// publish the control file
		final Exchange exchange = sendControlFile(id, control, "payload contents");
		
		// verification
		Assert.assertFalse(exchange.isFailed());
		payloadDestination.assertIsSatisfied(20);
		assertFilesWereIgnored();		
	}
	
	@Test
	public void testNonTransferEventControlFileIsIgnored() throws Exception {
		final String id = "1234";
		
		final ControlFile control = createTransferControlFile(id);
		control.setWorkflowId("workflow-1");
		control.getStatusRecord().setEvent(Event.COLLECT); // not TRANSFER
		control.getStatusRecord().setStatus(Status.SUCCESS);
		control.getStatusRecord().setStatusCode("00");
		
		// expectations
		payloadDestination.expectedMessageCount(0);
		
		// publish the control file
		final Exchange exchange = sendControlFile(id, control, "payload contents");
		
		// verification
		Assert.assertFalse(exchange.isFailed());
		payloadDestination.assertIsSatisfied(20);
		assertFilesWereIgnored();		
	}
	
	@Test
	public void testControlFileToUnknownMailboxIsIgnored() throws Exception {
		final String id = "1234";
		
		final ControlFile control = createTransferControlFile(id);
		control.setWorkflowId("workflow-1");
		control.getStatusRecord().setEvent(Event.TRANSFER);
		control.getStatusRecord().setStatus(Status.SUCCESS);
		control.getStatusRecord().setStatusCode("00");
		control.setToDTS("unknown-mailbox"); // invalid mailbox
		
		// expectations
		payloadDestination.expectedMessageCount(0);
		
		// publish the control file
		final Exchange exchange = sendControlFile(id, control, "payload contents");
		
		// verification
		Assert.assertFalse(exchange.isFailed());
		payloadDestination.assertIsSatisfied(20);
		assertFilesWereIgnored();		
	}
	
	@Test
	public void testWhenDataFileDoesNotExistThenControlFileIsMovedToErrorFolder() throws Exception {
		final String id = "1234";
		
		final ControlFile control = createTransferControlFile(id);
		control.setWorkflowId("workflow-1");
		control.getStatusRecord().setStatus(Status.SUCCESS);
		control.getStatusRecord().setStatusCode("00");
		
		// expectations
		payloadDestination.expectedMessageCount(0);
		
		// publish the control file
		final Exchange exchange = sendControlFile(id, control, null); // data file does not exist
		
		// verification
		Assert.assertTrue(exchange.isFailed());
		payloadDestination.assertIsSatisfied(20);
		Mockito.verify(route).moveFile(controlFile, new File(errorFolder, controlFile.getName()));
	}
	
	@Test
	public void testWhenPayloadCannotBePublishedThenControlFileAndDataFileAreMovedToErrorFolder() throws Exception {
		final String id = "1234";
		
		final ControlFile control = createTransferControlFile(id);
		control.setWorkflowId("workflow-1");
		control.getStatusRecord().setStatus(Status.SUCCESS);
		control.getStatusRecord().setStatusCode("00");
		
		// expectations
		payloadDestination.expectedBodiesReceived("payload");
		payloadDestination.whenAnyExchangeReceived(new Processor() {
			@Override
			public void process(Exchange exchange) throws Exception {
				throw new Exception("Simulating payload publishing error");
			}
		});
		
		// publish the control file
		final Exchange exchange = sendControlFile(id, control, "payload");
		
		// verification
		Assert.assertTrue(exchange.isFailed());
		payloadDestination.assertIsSatisfied(20);
		
		Thread.sleep(100); // completion hook runs *after* the exchange has returned!
		Mockito.verify(route).moveFile(controlFile, new File(errorFolder, controlFile.getName()));
	}
	
	private void assertControlFileHeadersWerePropagated(final String workflowId) {
		final Message payload = payloadDestination.getReceivedExchanges().get(0).getIn();
		Assert.assertEquals(workflowId, payload.getHeader(DTSHeaders.HEADER_WORKFLOW_ID));
		Assert.assertEquals("from-dts", payload.getHeader(DTSHeaders.HEADER_FROM_DTS));
		Assert.assertEquals("to-dts", payload.getHeader(DTSHeaders.HEADER_TO_DTS));
	}
	
	private void assertFilesWereDeleted() {
		Mockito.verify(route).deleteFile(controlFile);
		Mockito.verify(route).deleteFile(dataFile);
	}
	
	private void assertFilesWereIgnored() throws Exception {
		Mockito.verify(route, Mockito.times(0)).deleteFile(Mockito.any(File.class));
		Mockito.verify(route, Mockito.times(0)).moveFile(Mockito.any(File.class), Mockito.any(File.class));
	}
	
	private ControlFile createTransferControlFile(final String id) {
		final ControlFile control = new ControlFile();

		control.setLocalId(id);
		control.setMessageType(MessageType.Data);
		control.setAddressType(AddressType.DTS);
		control.setFromDTS("from-dts");
		control.setToDTS("to-dts");
		
		final StatusRecord statusRecord = new StatusRecord();
		statusRecord.setEvent(Event.TRANSFER);
		control.setStatusRecord(statusRecord);		
		control.applyDefaults();
		
		return control;
	}
	
	private Exchange sendControlFile(final String id, final ControlFile control, final Object data) {
		final Exchange exchange = new DefaultExchange(context);
		exchange.setPattern(ExchangePattern.InOut); // ensures we block until exchange is completed
		final Message message = exchange.getIn();
		
		controlFile = new File(inputFolder, id + ".ctl");
		message.setHeader(Exchange.FILE_NAME, controlFile.getName());
		message.setHeader(Exchange.FILE_PATH, controlFile.getPath());
		message.setHeader(Exchange.FILE_PARENT, inputFolder.getPath());
		message.setBody(control);
		
		if (data != null) {
			dataFile = new File(inputFolder, id + ".dat");
			dataFiles.put(dataFile, data);
		}
		
		return producerTemplate.send("stub:file://./target/example", exchange);
	}
}
