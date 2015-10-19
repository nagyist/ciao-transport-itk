package uk.nhs.ciao.transport.dts.route;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
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

import uk.nhs.ciao.camel.CamelUtils;
import uk.nhs.ciao.dts.AddressType;
import uk.nhs.ciao.dts.ControlFile;
import uk.nhs.ciao.dts.Event;
import uk.nhs.ciao.dts.MessageType;
import uk.nhs.ciao.dts.Status;
import uk.nhs.ciao.dts.StatusRecord;
import uk.nhs.ciao.transport.dts.address.DTSEndpointAddress;
import uk.nhs.ciao.transport.dts.sequence.IdGenerator;
import uk.nhs.ciao.transport.itk.envelope.Address;
import uk.nhs.ciao.transport.itk.envelope.DistributionEnvelope;
import uk.nhs.ciao.transport.itk.envelope.DistributionEnvelope.ManifestItem;

/**
 * Unit tests for {@link DTSDistributionEnvelopeSenderRoute}
 */
public class DTSDistributionEnvelopeSenderRouteTest {
	private SimpleRegistry registry;
	private CamelContext context;
	private ProducerTemplate producerTemplate;
	private DTSDistributionEnvelopeSenderRoute route;
	private IdGenerator idGenerator;
	private MockEndpoint dtsMessageSender;
	private MockEndpoint notificationPayloadReceiver;
	private volatile String toDTS = "receiver";
	
	@Before
	public void setup() throws Exception {
		registry = new SimpleRegistry();
		context = new DefaultCamelContext(registry);
		
		producerTemplate = new DefaultProducerTemplate(context);
		setupRoute();
		
		context.start();
		producerTemplate.start();
	}
	
	@After
	public void tearDown() throws Exception {
		CamelUtils.stopQuietly(producerTemplate, context);
	}
	
	private void setupRoute() throws Exception {
		route = new DTSDistributionEnvelopeSenderRoute();
		route.setDistributionEnvelopeSenderUri("stub:seda:distribution-envelope-sender");
		route.setDTSMessageSenderUri("mock:dts-message-sender");
		route.setDTSTemporaryFolder("../sender-temp/");		
		route.setDTSMessageSendNotificationReceiverUri("stub:send:send-notification-sender");
		route.setDistributionEnvelopeResponseUri("mock:notification-payload-receiver");
		
		registry.put("dtsSentIdempotentRepository", new MemoryIdempotentRepository());
		route.setIdempotentRepositoryId("dtsSentIdempotentRepository");
		
		registry.put("dtsSentInProgressRepository", new MemoryIdempotentRepository());
		route.setInProgressRepositoryId("dtsSentInProgressRepository");
		
		idGenerator = Mockito.mock(IdGenerator.class);
		route.setIdGenerator(idGenerator);
		
		route.setEndpointAddressEnricherUri("direct:endpoint-address-enricher");
		
		final ControlFile controlFile = new ControlFile();
		controlFile.setFromDTS("sender");
		controlFile.setWorkflowId("sender-workflow");
		route.setPrototypeControlFile(controlFile);
		
		context.addRoutes(route);
		
		// Always resolve to the specified DTS address
		context.addRoutes(new RouteBuilder() {			
			@Override
			public void configure() throws Exception {
				from("direct:endpoint-address-enricher")
					.process(new Processor() {
						@Override
						public void process(final Exchange exchange) throws Exception {
							final DTSEndpointAddress address = exchange.getIn().getBody(DTSEndpointAddress.class);
							address.setDtsMailbox(toDTS);
							exchange.getIn().setBody(address);
						}
					})
				.end();
			}
		});
		
		dtsMessageSender = MockEndpoint.resolve(context, "mock:dts-message-sender");
		dtsMessageSender.setSynchronous(true);
		notificationPayloadReceiver = MockEndpoint.resolve(context, "mock:notification-payload-receiver");
		notificationPayloadReceiver.setSynchronous(true);
	}
	
	@Test
	public void testSendDistributionEnvelope() throws Exception {
		final String id = "1234";
		final String seqNum = "1";
		Mockito.when(idGenerator.generateId()).thenReturn(seqNum);
		
		final DistributionEnvelope envelope = new DistributionEnvelope();
		envelope.setTrackingId(id);

		final Address receiverAddress = new Address();
		receiverAddress.setODSCode("receiver");
		envelope.getAddresses().add(receiverAddress);

		final Address senderAddress = new Address();
		senderAddress.setODSCode("sender");
		envelope.setSenderAddress(senderAddress);
		
		final ManifestItem manifestItem = new ManifestItem();
		manifestItem.setMimeType("text/plain");
		envelope.addPayload(manifestItem, "payload body");
		
		envelope.getHandlingSpec().setBusinessAckRequested(false);
		envelope.getHandlingSpec().setInfrastructureAckRequested(false);
		envelope.getHandlingSpec().setInteration("interaction");
		
		envelope.applyDefaults();
		
		// expectations
		dtsMessageSender.expectedMessageCount(2);
			
		// publish the distribution envelope
		final Exchange exchange = new DefaultExchange(context);
		exchange.setPattern(ExchangePattern.InOut);
		exchange.getIn().setHeader(Exchange.CORRELATION_ID, id);
		exchange.getIn().setBody(envelope, String.class);
		
		producerTemplate.send("stub:seda:distribution-envelope-sender", exchange);
		
		// verify result
		dtsMessageSender.assertIsSatisfied(50);
		
		// the first output should be the data file - the second should be the control file
		final Message dataMessage = dtsMessageSender.getExchanges().get(0).getIn();
		final Message controlMessage = dtsMessageSender.getExchanges().get(1).getIn();
		
		Assert.assertEquals(seqNum + ".dat", dataMessage.getHeader(Exchange.FILE_NAME));
		Assert.assertEquals(seqNum + ".ctl", controlMessage.getHeader(Exchange.FILE_NAME));
		
		final DistributionEnvelope dataEnvelope = dataMessage.getMandatoryBody(DistributionEnvelope.class);
		Assert.assertEquals(id, dataEnvelope.getTrackingId());
		
		final ControlFile controlFile = controlMessage.getMandatoryBody(ControlFile.class);
		Assert.assertEquals(id, controlFile.getLocalId());
		Assert.assertEquals(toDTS, controlFile.getToDTS());
	}
	
	@Test
	public void testSuccessSendNotificationIsPublished() throws Exception {
		final String id = "1234";
		final ControlFile controlFile = generateControlFile(id);
		
		final StatusRecord statusRecord = new StatusRecord();
		statusRecord.setEvent(Event.TRANSFER);
		statusRecord.setStatus(Status.SUCCESS);
		statusRecord.applyDefaults();
		controlFile.setStatusRecord(statusRecord);
		
		// expectations
		notificationPayloadReceiver.expectedMessageCount(1);
		
		// publish the notification
		sendNotification(controlFile);
		
		// verify result
		notificationPayloadReceiver.assertIsSatisfied(50);
		
		final Message message = notificationPayloadReceiver.getExchanges().get(0).getIn();
		Assert.assertEquals("sent", message.getHeader("ciao.messageSendNotification"));
		Assert.assertEquals(id, message.getHeader(Exchange.CORRELATION_ID));
	}
	
	@Test
	public void testFailureSendNotificationIsPublished() throws Exception {
		final String id = "1234";
		final ControlFile controlFile = generateControlFile(id);
		
		final StatusRecord statusRecord = new StatusRecord();
		statusRecord.setEvent(Event.TRANSFER);
		statusRecord.setStatus(Status.ERROR);
		statusRecord.applyDefaults();
		controlFile.setStatusRecord(statusRecord);
		
		// expectations
		notificationPayloadReceiver.expectedMessageCount(1);
		
		// publish the notification
		sendNotification(controlFile);
		
		// verify result
		notificationPayloadReceiver.assertIsSatisfied(50);
		
		final Message message = notificationPayloadReceiver.getExchanges().get(0).getIn();
		Assert.assertEquals("send-failed", message.getHeader("ciao.messageSendNotification"));
		Assert.assertEquals(id, message.getHeader(Exchange.CORRELATION_ID));
	}
	
	@Test
	public void testNonTransferSendNotificationIsIgnored() throws Exception {
		final String id = "1234";
		final ControlFile controlFile = generateControlFile(id);
		
		final StatusRecord statusRecord = new StatusRecord();
		statusRecord.setEvent(Event.COLLECT); // not a transfer event
		statusRecord.setStatus(Status.SUCCESS);
		statusRecord.applyDefaults();
		controlFile.setStatusRecord(statusRecord);
		
		// expectations
		notificationPayloadReceiver.expectedMessageCount(0);
		
		// publish the notification
		sendNotification(controlFile);
		
		// verify result
		notificationPayloadReceiver.assertIsSatisfied(50);
	}
	
	private Exchange sendNotification(final ControlFile controlFile) {
		final Exchange exchange = new DefaultExchange(context);
		exchange.setPattern(ExchangePattern.InOut);
		
		exchange.getIn().setHeader(Exchange.FILE_NAME, controlFile.getLocalId() + ".ctl");
		exchange.getIn().setBody(controlFile, String.class);
		
		return producerTemplate.send("stub:send:send-notification-sender", exchange);
	}
	
	private ControlFile generateControlFile(final String id) {
		final ControlFile controlFile = new ControlFile();
		controlFile.setAddressType(AddressType.DTS);
		controlFile.setFromDTS("sender");
		controlFile.setToDTS(toDTS);
		controlFile.setWorkflowId("sender-workflow");
		controlFile.setLocalId(id);
		controlFile.setMessageType(MessageType.Data);
		controlFile.applyDefaults();
		return controlFile;
	}
}
