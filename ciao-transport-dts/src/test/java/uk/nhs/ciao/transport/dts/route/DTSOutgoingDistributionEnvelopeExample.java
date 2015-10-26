package uk.nhs.ciao.transport.dts.route;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.util.FileUtil;
import org.joda.time.LocalDateTime;

import com.google.common.base.Strings;

import uk.nhs.ciao.camel.CamelUtils;
import uk.nhs.ciao.dts.ControlFile;
import uk.nhs.ciao.dts.Event;
import uk.nhs.ciao.dts.Status;
import uk.nhs.ciao.dts.StatusRecord;
import uk.nhs.ciao.transport.dts.address.DTSEndpointAddress;
import uk.nhs.ciao.transport.dts.processor.DTSFileHousekeeper;
import uk.nhs.ciao.transport.dts.sequence.UUIDGenerator;
import uk.nhs.ciao.transport.itk.envelope.Address;
import uk.nhs.ciao.transport.itk.envelope.DistributionEnvelope;
import uk.nhs.ciao.transport.itk.envelope.DistributionEnvelope.ManifestItem;

/**
 * Example class showing how outgoing pairs of DTS *.ctl and *.dat files and associated
 * send notification are handled.
 * <p>
 * Incoming notification is logged to the console.
 */
public class DTSOutgoingDistributionEnvelopeExample implements RoutesBuilder {	
	public static void main(final String[] args) throws Exception {
		final SimpleRegistry registry = new SimpleRegistry();
		final CamelContext context = new DefaultCamelContext(registry);
		try {
			context.addRoutes(new DTSOutgoingDistributionEnvelopeExample(registry));
			context.start();
			new CountDownLatch(1).await();
		} finally {
			CamelUtils.stopQuietly(context);
		}
	}

	private final SimpleRegistry registry;
	
	public DTSOutgoingDistributionEnvelopeExample(final SimpleRegistry registry) {
		this.registry = registry;
	}
	
	@Override
	public void addRoutesToCamelContext(final CamelContext context) throws Exception {
		addInputDirectoryMonitor(context);
		addDTSDistributionEnvelopeSender(context);
		addMockResponder(context);
		addNotificationReceiver(context);
		addEndpointAddressEnricher(context);
	}
	
	// Monitor a folder for documents
	// the filename will be used as the document tracking id
	// and a canned distribution envelope will be send to the sender
	// The contents of the file will be added to the payload
	// The mock responder will generate a send notification based on the contents of
	// the file - if it is "OK" the send is successful otherwise it is not
	private void addInputDirectoryMonitor(final CamelContext context) throws Exception {
		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				from("file://./target/dts/sender-in?delete=true").id("input-directory-monitor")
					.setHeader(Exchange.CORRELATION_ID).header(Exchange.FILE_NAME)
					.bean(new DistributionEnvelopeCreator())
					.log("Will send distribution envelope for documentId=${header.CamelCorrelationId}")
					.to("seda:distribution-envelope-sender")
				.end();
			}
		});
	}
	
	private void addMockResponder(final CamelContext context) throws Exception {
		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				from("file://./target/dts/OUT?move=../responder-handled&include=.*\\.ctl").id("mock-responder")
					.bean(new SendNotificationGenerator())
					.to("file://./target/dts/SENT?fileName=${header.CamelFileName}&tempPrefix=../responder-temp/")
				.end();
			}
		});
	}
	
	public class SendNotificationGenerator {
		public ControlFile generateNotification(final TypeConverter typeConverter,
				@Header(Exchange.FILE_PARENT) final File parent,
				@Header(Exchange.FILE_NAME) final String controlFileName,
				@Body final ControlFile controlFile) throws Exception {
			final File dataFile = new File(parent, controlFileName.substring(0, controlFileName.length() - 3) + "dat");
			
			final DistributionEnvelope envelope = typeConverter.mandatoryConvertTo(DistributionEnvelope.class, dataFile);
			final String payload = envelope.getPayloads().get(0).getBody();
			
			final File moveFolder = new File(parent, "../responder-handled");
			if (!moveFolder.exists()) {
				moveFolder.mkdirs();
			}
			
			FileUtil.renameFile(dataFile, new File(moveFolder, dataFile.getName()), true);
			
			final ControlFile notification = new ControlFile();
			notification.copyFrom(controlFile, true);
			
			final StatusRecord statusRecord = new StatusRecord();
			statusRecord.setEvent(Event.TRANSFER);
			statusRecord.setDateTime(LocalDateTime.now());
			
			if (Strings.nullToEmpty(payload).trim().equals("OK")) {
				statusRecord.setStatus(Status.SUCCESS);
				statusRecord.setStatusCode("00");
			} else {
				statusRecord.setStatus(Status.ERROR);
				statusRecord.setStatusCode("01");
			}
			notification.setStatusRecord(statusRecord);
			
			notification.applyDefaults();
			
			return notification;
		}
	}
	
	public class DistributionEnvelopeCreator {
		public DistributionEnvelope createDistributionEnvelope(@Header(Exchange.CORRELATION_ID) final String trackingId,
				@Body final String payload) {
			final DistributionEnvelope envelope = new DistributionEnvelope();
			envelope.setService("service");
			envelope.setTrackingId(trackingId);
			
			envelope.getHandlingSpec().setInfrastructureAckRequested(false);
			envelope.getHandlingSpec().setBusinessAckRequested(false);
			envelope.getHandlingSpec().setInteration("interaction");
			
			envelope.addAddress(new Address(Address.ODS_TYPE, "to-ods"));
			
			final ManifestItem manifestItem = new ManifestItem();
			manifestItem.setMimeType("text/plain");
			envelope.addPayload(manifestItem, Strings.nullToEmpty(payload).trim());
			
			return envelope;
		}
	}
	
	private void addDTSDistributionEnvelopeSender(final CamelContext context) throws Exception {
		final DTSDistributionEnvelopeSenderRoute route = new DTSDistributionEnvelopeSenderRoute();
		
		route.setDistributionEnvelopeSenderUri("seda:distribution-envelope-sender");
		route.setDTSMessageSenderUri("file://./target/dts/OUT");
		route.setDTSTemporaryFolder("../sender-temp/");		
		route.setDTSMessageSendNotificationReceiverUri("direct:dts-send-notification-receiver");
		route.setDistributionEnvelopeResponseUri("direct:notification-receiver");
		route.setIdGenerator(new UUIDGenerator());
		route.setErrorFileHousekeeper(new DTSFileHousekeeper("error"));
		route.setEndpointAddressEnricherUri("direct:endpoint-address-enricher");
		
		final ControlFile controlFile = new ControlFile();
		controlFile.setFromDTS("sender");
		controlFile.setWorkflowId("sender-workflow");
		route.setPrototypeControlFile(controlFile);
		
		context.addRoutes(route);
		
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
		router.setDTSMessageSendNotificationReceiverUri("direct:dts-send-notification-receiver");
		router.setDTSSentUri("file://./target/dts/SENT");
		router.setDTSMessageReceiverUri("mock:dts-message-receiver?retainLast=1");
		router.setMailboxes(Arrays.asList("sender"));
		router.setWorkflowIds(Arrays.asList("sender-workflow"));
		context.addRoutes(router);
	}
	
	private void addNotificationReceiver(final CamelContext context) throws Exception {
		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				from("direct:notification-receiver").id("notification-receiver")
					.log("Received DTS send notification: documentId=${header.CamelCorrelationId}, type=${header[ciao.messageSendNotification]}\n${body}")
				.end();
			}
		});
	}
	
	private void addEndpointAddressEnricher(final CamelContext context) throws Exception {
		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				from("direct:endpoint-address-enricher")
					.bean(new AddressEnricher())
				.end();
			}
		});
	}
	
	// bypass the normal enricher - always return a known static value
	public class AddressEnricher {
		public DTSEndpointAddress addDTSAddress(@Body final DTSEndpointAddress address) {
			if (address != null) {
				address.setDtsMailbox("receiver");
			}
			
			return address;
		}
	}
}
