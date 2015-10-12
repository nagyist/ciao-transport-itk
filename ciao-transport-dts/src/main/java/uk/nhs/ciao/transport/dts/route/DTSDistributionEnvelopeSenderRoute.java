package uk.nhs.ciao.transport.dts.route;

import static org.apache.camel.builder.PredicateBuilder.*;
import static uk.nhs.ciao.logging.CiaoCamelLogMessage.camelLogMsg;

import java.net.URI;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Property;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.util.URISupport;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;

import uk.nhs.ciao.dts.AddressType;
import uk.nhs.ciao.dts.ControlFile;
import uk.nhs.ciao.dts.MessageType;
import uk.nhs.ciao.logging.CiaoCamelLogger;
import uk.nhs.ciao.transport.dts.address.DTSEndpointAddress;
import uk.nhs.ciao.transport.itk.envelope.Address;
import uk.nhs.ciao.transport.itk.envelope.DistributionEnvelope;
import uk.nhs.ciao.transport.itk.route.DistributionEnvelopeSenderRoute;

public class DTSDistributionEnvelopeSenderRoute extends DistributionEnvelopeSenderRoute {
	private static final CiaoCamelLogger LOGGER = CiaoCamelLogger.getLogger(DTSDistributionEnvelopeSenderRoute.class);
	
	private String dtsMessageSenderUri;
	private String dtsMessageSendNotificationReceiverUri;
	private String dtsTemporaryFolder;
	private IdempotentRepository<?> idempotentRepository;
	private IdempotentRepository<?> inProgressRepository;
	
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
	 * Idempotent repository used to track processed sent notifications
	 */
	public void setIdempotentRepository(final IdempotentRepository<?> idempotentRepository) {
		this.idempotentRepository = idempotentRepository;
	}
	
	/**
	 * Idempotent repository to use to track in-progress end notifications
	 */
	public void setInProgressRepository(final IdempotentRepository<?> inProgressRepository) {
		this.inProgressRepository = inProgressRepository;
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
				.when().simple("${getDtsMailbox} == null")
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
//				.ebxmlMessageId("${property.ebxmlManifest.messageData.messageId}")
//				.service("${property.ebxmlManifest.service}")
//				.action("${property.ebxmlManifest.action}")
//				.receiverAsid("${property.destination?.asid}")
//				.receiverODSCode("${property.destination?.odsCode}")
//				.receiverMHSPartyKey("${property.ebxmlManifest.toParty}")
				.eventName("constructed-dts-message")))
			
			// write files through a temporary folder (to avoid the client process reading files before they are fully written)
			.setHeader("tempPrefix").constant(dtsTemporaryFolder)
				
			// first write the data file
			.setBody().property("distributionEnvelope")
			.convertBodyTo(String.class)
			.setHeader(Exchange.FILE_NAME).simple(dtsFilePrefix + "${property.distributionEnvelope.trackingId}.dat")
			.to(dtsMessageSenderUri)
			
			// then write the control file
			.setBody().property("controlFile")
			.convertBodyTo(String.class)
			.setHeader(Exchange.FILE_NAME).simple(dtsFilePrefix + "${property.distributionEnvelope.trackingId}.ctl")
			.to(dtsMessageSenderUri)
		.end();
	}
	
	private void configureSendNotificationReceiver() throws Exception {
		// Additional configuration parameters are appended to the endpoint URI
		final Map<String, Object> endpointParams = Maps.newLinkedHashMap();
		
		// only process files intended for this application
		if (!Strings.isNullOrEmpty(dtsFilePrefix)) {
			endpointParams.put("include", Pattern.quote(dtsFilePrefix) + ".+");
		}
		
		// only process each file once
		endpointParams.put("idempotent", true);
		endpointParams.put("idempotentRepository", idempotentRepository);
		endpointParams.put("inProgressRepository", inProgressRepository);
		endpointParams.put("readLock", "idempotent");
		
		// delete after processing
		// TODO: Should these be moved to another directory instead of delete?
		endpointParams.put("delete", true);
		
		// Cleanup the repository after processing / delete
		// The readLockRemoveOnCommit option is not available in this version of camel
		// The backing repository will need additional configuration to expunge old entries
		// If using hazelcast the backing map can be configured with a max time to live for each key
//		endpointParams.put("readLockRemoveOnCommit", true);
		
		from(URISupport.createRemainingURI(URI.create(dtsMessageSendNotificationReceiverUri), endpointParams).toString())
			.choice()
				.when(not(endsWith(header(Exchange.FILE_NAME), constant(".ctl"))))
					/*
					 * only interested in processing control files
					 * this is not handled in the main consumer include parameter because
					 * the files still need housekeeping to be applied (move/delete etc)
					 */
					.stop()
				.endChoice()
			.end()
		
			.setProperty("original-body").body() // maintain original serialised form
			.convertBodyTo(ControlFile.class)

			.choice()
				.when().simple("${body.statusRecord?.event} != ${type:uk.nhs.ciao.dts.Event.TRANSFER}")
					// only interested in TRANSFER events
					.stop()
				.endChoice()
			.end()
			
			.setHeader(Exchange.CORRELATION_ID).simple("${body.localId}")
			
			.process(LOGGER.info(camelLogMsg("Received DTS message send notification")
				.documentId(header(Exchange.CORRELATION_ID))
				.itkTrackingId("${body.localId}")
//				.ebxmlMessageId("${body.messageData.messageId}")
//				.ebxmlRefToMessageId("${body.messageData.refToMessageId}")
//				.service("${body.service}")
//				.action("${body.action}")
//				.receiverMHSPartyKey("${body.toParty}")
				.eventName("received-dts-sent-response")))
					
			.choice()
				.when().simple("${body.statusRecord?.status} == ${type:uk.nhs.ciao.dts.StatusRecord.SUCCESS}")
					/*
					 * TODO: Check the spec to confirm this - it looks as if statusCode == "00" may be a better check
					 * SUCCESS might also be returned on statusCode == "05" -> Transfer error - retry!
					 */
					.setHeader("ciao.messageSendNotification", constant("sent"))
				.otherwise()
					.setHeader("ciao.messageSendNotification", constant("send-failed"))
				.endChoice()
			.end()
			
			.setBody().property("original-body") // restore original serialised form
			.to(getDistributionEnvelopeResponseUri())
		.end();
	}
	
	// Processor / bean methods
	// The methods can't live in the route builder - it causes havoc with the debug/tracer logging
	
	public class DestinationAddressBuilder {
		public DTSEndpointAddress buildDestinationAdddress(final DistributionEnvelope envelope) {
			final DTSEndpointAddress destination = new DTSEndpointAddress();
			
			final Address address = envelope.getAddresses().get(0);
			if (address.isDTS()) {
				destination.setDtsMailbox(address.getUri());
			} else if (address.isODS()) {
				destination.setOdsCode(address.getODSCode());
			}
			
			// TODO: workflow ID may be different for business messages and acks
			// 1) Propagate/resolve from incoming workflowId header [not implemented yet] (when sending response messages)
			// 2) Fall-back to prototype control file otherwise
			if (prototypeControlFile != null) {
				destination.setWorkflowId(prototypeControlFile.getWorkflowId());
			}
			
			return destination;
		}
	}
	
	/**
	 * Builds a ControlFile for the specified DistributionEnvelope
	 */
	public class ControlFileBuilder {
		public ControlFile buildControlFile(@Property("distributionEnvelope") final DistributionEnvelope envelope,
				@Property("destination") final DTSEndpointAddress destination) {
			final ControlFile controlFile = new ControlFile();
			
			controlFile.setMessageType(MessageType.Data);
			controlFile.setAddressType(AddressType.DTS);
			
			if (!Strings.isNullOrEmpty(destination.getDtsMailbox())) {
				controlFile.setToDTS(destination.getDtsMailbox());
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
