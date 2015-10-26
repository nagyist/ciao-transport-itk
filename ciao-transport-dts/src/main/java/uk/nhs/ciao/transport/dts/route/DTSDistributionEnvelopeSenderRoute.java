package uk.nhs.ciao.transport.dts.route;

import static uk.nhs.ciao.logging.CiaoCamelLogMessage.camelLogMsg;
import static uk.nhs.ciao.transport.dts.route.DTSHeaders.*;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.Predicate;
import org.apache.camel.Property;
import org.apache.camel.util.FileUtil;

import com.google.common.base.Strings;

import uk.nhs.ciao.dts.AddressType;
import uk.nhs.ciao.dts.ControlFile;
import uk.nhs.ciao.dts.MessageType;
import uk.nhs.ciao.dts.Status;
import uk.nhs.ciao.logging.CiaoCamelLogger;
import uk.nhs.ciao.transport.dts.address.DTSEndpointAddress;
import uk.nhs.ciao.transport.dts.sequence.IdGenerator;
import uk.nhs.ciao.transport.itk.envelope.Address;
import uk.nhs.ciao.transport.itk.envelope.DistributionEnvelope;
import uk.nhs.ciao.transport.itk.route.DistributionEnvelopeSenderRoute;
import uk.nhs.ciao.transport.itk.route.ItkDocumentSenderRoute;

public class DTSDistributionEnvelopeSenderRoute extends DistributionEnvelopeSenderRoute {
	private static final CiaoCamelLogger LOGGER = CiaoCamelLogger.getLogger(DTSDistributionEnvelopeSenderRoute.class);
	
	private String dtsMessageSenderUri;
	private String dtsMessageSendNotificationReceiverUri;
	private String dtsTemporaryFolder;
	private String dtsErrorFolder;
	private IdGenerator idGenerator;
	
	// optional properties
	private ControlFile prototypeControlFile;
	private String dtsFilePrefix = "";
	
	/**
	 * URI of the DTS outbox
	 * <p>
	 * output only
	 */
	public void setDTSMessageSenderUri(String dtsMessageSenderUri) {
		this.dtsMessageSenderUri = dtsMessageSenderUri;
	}
	
	/**
	 * URI of the DTS sent message box
	 * <p>
	 * input only
	 */
	public void setDTSMessageSendNotificationReceiverUri(final String dtsMessageSendNotificationReceiverUri) {
		this.dtsMessageSendNotificationReceiverUri = dtsMessageSendNotificationReceiverUri;
	}
	
	/**
	 * The location of the temporary folder used to initially write files to
	 * (prior) to atomically moving them to {@link #setDistributionEnvelopeSenderUri(String)}
	 * <p>
	 * The temporary folder should exist on the same file system as the send folder so that 
	 * a file move can be performed.
	 */
	public void setDTSTemporaryFolder(final String dtsTemporaryFolder) {
		this.dtsTemporaryFolder = dtsTemporaryFolder;
	}
	
	/**
	 * Folder files should be moved to if an error occurs while processing
	 * <p>
	 * Only files intended for this application will be moved, other files will be left in the
	 * processing folder.
	 */
	public void setDTSErrorFolder(final String dtsErrorFolder) {
		this.dtsErrorFolder = dtsErrorFolder;
	}

	/**
	 * Generator for generating DTS transaction IDs
	 */
	public void setIdGenerator(final IdGenerator idGenerator) {
		this.idGenerator = idGenerator;
	}
	
	/**
	 * Sets the prototype control file containing default properties that should
	 * be added to all control files before they are sent.
	 * <p>
	 * Non-empty properties on the control file being sent are not overwritten.
	 * 
	 * @param prototype The prototype control file
	 */
	public void setPrototypeControlFile(final ControlFile prototype) {
		prototypeControlFile = prototype;
	}
	
	/**
	 * A prefix to be added to DTS file names before they are sent to the client.
	 * <p>
	 * The DTS documentation recommends <code>${siteid}${APP}</code>:
	 * <ul>
	 * <li>siteId: will uniquely identify the client site. The DTS Client userid should be used for this value.</li>
	 * <li>APP: an optional value used to allow different applications on the platform to use the same client to transfer files.</li>
	 * </ul>
	 */
	public void setDTSFilePrefix(final String dtsFilePrefix) {
		this.dtsFilePrefix = Strings.nullToEmpty(dtsFilePrefix);
	}
	
	@Override
	public void configure() throws Exception {
		configureRequestSender();
		configureSendNotificationReceiver();
	}
	
	private void configureRequestSender() throws Exception {
		/*
		 * The output will be two files: a data file and a control file - individual parts
		 * are constructed and stored as properties until the bodies are written in the final stage 
		 */
		from(getDistributionEnvelopeSenderUri())				
			// Configure the distribution envelope
			.convertBodyTo(DistributionEnvelope.class)
			.bean(new DistributionEnvelopePopulator())
			.setProperty("distributionEnvelope").body()
			
			// Resolve destination address
			.bean(new DestinationAddressBuilder())
			.choice()
				.when().simple("${body.getDtsMailbox} == null")
					.to(getEndpointAddressEnricherUri())
				.endChoice()
			.end()
			.setProperty("destination").body()

			// Configure control file
			.bean(new ControlFileBuilder())
			.setProperty("controlFile").body()
			
			.process(LOGGER.info(camelLogMsg("Constructed DTS message for sending")
				.documentId(header(Exchange.CORRELATION_ID))
				.itkTrackingId("${property.distributionEnvelope.trackingId}")
				.distributionEnvelopeService("${property.distributionEnvelope.service}")
				.interactionId("${property.distributionEnvelope.handlingSpec.getInteration}")
				.workflowId("${property.controlFile.getWorkflowId}")
				.fromDTS("${property.controlFile.getFromDTS}")
				.toDTS("${property.controlFile.getToDTS}")
				.eventName("constructed-dts-message")))
			
			// write files through a temporary folder (to avoid the client process reading files before they are fully written)
			.setHeader("tempPrefix").constant(dtsTemporaryFolder)
			
			// Obtain an id for the DTS transaction
			.setProperty("dtsTransactionId").method(idGenerator, "generateId")
			
			// first write the data file
			.setBody().property("distributionEnvelope")
			.convertBodyTo(String.class)
			.setHeader(Exchange.FILE_NAME).simple(dtsFilePrefix + "${property.dtsTransactionId}.dat")
			.to(dtsMessageSenderUri)
			
			// then write the control file
			.setBody().property("controlFile")
			.convertBodyTo(String.class)
			.setHeader(Exchange.FILE_NAME).simple(dtsFilePrefix + "${property.dtsTransactionId}.ctl")
			.to(dtsMessageSenderUri)
		.end();
	}
	
	private void configureSendNotificationReceiver() throws Exception {
		from(dtsMessageSendNotificationReceiverUri)
			.convertBodyTo(ControlFile.class)
			.setHeader(Exchange.CORRELATION_ID).simple("${body.localId}")
			.setHeader(ItkDocumentSenderRoute.ITK_CORRELATION_ID_HEADER).header(Exchange.CORRELATION_ID)
			
			.process(LOGGER.info(camelLogMsg("Received DTS message send notification")
				.documentId(header(Exchange.CORRELATION_ID))
				.itkTrackingId("${body.localId}")
				.workflowId("${body.getWorkflowId}")
				.fromDTS("${body.getFromDTS}")
				.toDTS("${body.getToDTS}")
				.eventName("received-dts-sent-response")))
					
			.choice()
				.when(new IsSuccessStatus())
					/*
					 * TODO: Check the spec to confirm this - it looks as if statusCode == "00" may be a better check
					 * SUCCESS might also be returned on statusCode == "05" -> Transfer error - retry!
					 */
					.setHeader("ciao.messageSendNotification", constant("sent"))
				.endChoice()
				.otherwise()
					.setHeader("ciao.messageSendNotification", constant("send-failed"))
				.endChoice()
			.end()
			
			.setBody().property("original-body") // restore original serialised form
			.to(getDistributionEnvelopeResponseUri())
			
			// on success - delete the control file
			.bean(new ControlFileDeleter())
		.end();
	}
	
	// protected scope (for unit testing)
	protected void deleteFile(final File file) {
		if (file != null && file.isFile()) {
			FileUtil.deleteFile(file);
		}
	}
	
	// Processor / bean methods
	// The methods can't live in the route builder - it causes havoc with the debug/tracer logging
	
	/**
	 * Deletes the control file
	 * <p>
	 * Alternative to use the delete=true option on the file component
	 */
	public class ControlFileDeleter {
		public void deleteControlFile(@Header(Exchange.FILE_PATH) final File file) {
			deleteFile(file);
		}
	}
	
	public class DestinationAddressBuilder {
		public DTSEndpointAddress buildDestinationAdddress(final DistributionEnvelope envelope,
				@Header(HEADER_WORKFLOW_ID) final String workflowId,
				@Header(HEADER_TO_DTS) final String toDTS) {
			final DTSEndpointAddress destination = new DTSEndpointAddress();
			
			// Use address header if specified otherwise build from envelope
			if (!Strings.isNullOrEmpty(toDTS)) {
				destination.setDtsMailbox(toDTS);
			} else if (!envelope.getAddresses().isEmpty()) {
				final Address address = envelope.getAddresses().get(0);
				if (address.isDTS()) {
					destination.setDtsMailbox(address.getUri());
				} else if (address.isODS()) {
					destination.setOdsCode(address.getODSCode());
				}
			}
			
			// 1) Propagate/resolve from incoming workflowId header (when sending response messages)
			// 2) Fall-back to prototype control file otherwise
			if (workflowId != null) {
				destination.setWorkflowId(workflowId);
			} else if (prototypeControlFile != null) {
				destination.setWorkflowId(prototypeControlFile.getWorkflowId());
			}
			
			return destination;
		}
	}
	
	private static class IsSuccessStatus implements Predicate {
		@Override
		public boolean matches(final Exchange exchange) {
			final ControlFile controlFile = exchange.getIn().getBody(ControlFile.class);
			if (controlFile == null || controlFile.getStatusRecord() == null) {
				return false;
			}
			return controlFile.getStatusRecord().getStatus() == Status.SUCCESS;
		}
	}
	
	/**
	 * Builds a ControlFile for the specified DistributionEnvelope
	 */
	public class ControlFileBuilder {
		public ControlFile buildControlFile(@Property("distributionEnvelope") final DistributionEnvelope envelope,
				@Property("destination") final DTSEndpointAddress destination,
				@Header(HEADER_FROM_DTS) final String fromDTS) {
			final ControlFile controlFile = new ControlFile();
			
			controlFile.setMessageType(MessageType.Data);
			controlFile.setAddressType(AddressType.DTS);
			
			// Propagate from header (if specified)
			if (!Strings.isNullOrEmpty(fromDTS)) {
				controlFile.setFromDTS(fromDTS);
			}
			
			if (!Strings.isNullOrEmpty(destination.getDtsMailbox())) {
				controlFile.setToDTS(destination.getDtsMailbox());
			}
			
			if (!Strings.isNullOrEmpty(destination.getWorkflowId())) {
				controlFile.setWorkflowId(destination.getWorkflowId());
			}

			controlFile.setLocalId(envelope.getTrackingId()); 
			
			// Apply defaults from the prototype
			final boolean overwrite = false;
			controlFile.copyFrom(prototypeControlFile, overwrite);
			controlFile.applyDefaults();
			
			return controlFile;
		}
	}
}
