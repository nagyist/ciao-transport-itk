package uk.nhs.ciao.transport.dts.route;

import static org.apache.camel.builder.PredicateBuilder.*;
import static uk.nhs.ciao.logging.CiaoCamelLogMessage.camelLogMsg;

import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;

import uk.nhs.ciao.camel.BaseRouteBuilder;
import uk.nhs.ciao.camel.URIBuilder;
import uk.nhs.ciao.dts.ControlFile;
import uk.nhs.ciao.dts.Event;
import uk.nhs.ciao.dts.MessageType;
import uk.nhs.ciao.logging.CiaoCamelLogger;

/**
 * Monitors the DTS incoming message folders (IN, SENT) for control files
 * and routes them to an appropriate handler.
 * <p>
 * Housekeeping/cleanup of the folders is handled by delegate route.
 */
public class DTSIncomingFileRouterRoute extends BaseRouteBuilder {
	private static final CiaoCamelLogger LOGGER = CiaoCamelLogger.getLogger(DTSIncomingFileRouterRoute.class);
	
	// sources
	private String dtsInUri;
	private String dtsSentUri;
	
	// targets
	private String dtsMessageReceiverUri;
	private String dtsMessageSendNotificationReceiverUri;
	
	private String inIdempotentRepositoryId;
	private String inInProgressRepositoryId;
	private String sentIdempotentRepositoryId;
	private String sentInProgressRepositoryId;
	
	private final Set<String> mailboxes = Sets.newHashSet();
	private final Set<String> workflowIds = Sets.newHashSet();
	
	// optional
	private String dtsFilePrefix = "";
	
	public void setDTSInUri(final String dtsInUri) {
		this.dtsInUri = dtsInUri;
	}
	
	public void setDTSSentUri(final String dtsSentUri) {
		this.dtsSentUri = dtsSentUri;
	}
	
	public void setDTSMessageReceiverUri(final String dtsMessageReceiverUri) {
		this.dtsMessageReceiverUri = dtsMessageReceiverUri;
	}
	
	public void setDTSMessageSendNotificationReceiverUri(final String dtsMessageSendNotificationReceiverUri) {
		this.dtsMessageSendNotificationReceiverUri = dtsMessageSendNotificationReceiverUri;
	}
	
	public void setInIdempotentRepositoryId(final String inIdempotentRepositoryId) {
		this.inIdempotentRepositoryId = inIdempotentRepositoryId;
	}
	
	public void setInInProgressRepositoryId(final String inInProgressRepositoryId) {
		this.inInProgressRepositoryId = inInProgressRepositoryId;
	}
	
	public void setSentIdempotentRepositoryId(final String sentIdempotentRepositoryId) {
		this.sentIdempotentRepositoryId = sentIdempotentRepositoryId;
	}
	
	public void setSentInProgressRepositoryId(final String sentInProgressRepositoryId) {
		this.sentInProgressRepositoryId = sentInProgressRepositoryId;
	}
	
	public void setMailboxes(final Collection<String> mailboxes) {
		this.mailboxes.clear();
		this.mailboxes.addAll(mailboxes);
	}
	
	public void setWorkflowIds(final Collection<String> workflowIds) {
		this.workflowIds.clear();
		this.workflowIds.addAll(workflowIds);
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
		configureSentFolderMonitor();
		configureInFolderMonitor();
	}
	

	private void configureSentFolderMonitor() throws Exception {
		// Additional configuration parameters are appended to the endpoint URI
		final URIBuilder uri = new URIBuilder(dtsSentUri);
		
		// only process files intended for this application
		if (!Strings.isNullOrEmpty(dtsFilePrefix)) {
			uri.set("include", Pattern.quote(dtsFilePrefix) + "..*\\.ctl");
		}
		
		// only process each file once
		uri.set("idempotent", true)
			.set("idempotentRepository", "#" + sentIdempotentRepositoryId)
			.set("inProgressRepository", "#" + sentInProgressRepositoryId)
			.set("readLock", "idempotent");
		
		// Unknown files should be kept in the repository (they can be expunged over time)
		// Moving of files is handled manually in the delegate route due to difficulties in disabling
		// the move option while using moveFailed
		uri.set("noop", true);
				
		from(uri.toString())
			/*
			 * only interested in processing control files
			 * this is not handled in the main consumer include parameter because
			 * the files still need housekeeping to be applied (move/delete etc)
			 */
			.filter(and(
					endsWith(header(Exchange.FILE_NAME), constant(".ctl")),
					isFromDTSKnown(),
					isWorkflowIdKnown(),
					isMessageType(MessageType.Data),
					isEvent(Event.TRANSFER)))
				.to(dtsMessageSendNotificationReceiverUri)
			.end()
		.end();
	}
	
	private void configureInFolderMonitor() throws Exception {
		// Additional configuration parameters are appended to the endpoint URI
		final URIBuilder uri = new URIBuilder(dtsInUri);
		
		// only handle control files
		uri.set("include", "..*\\.ctl");
				
		// only process each file once
		// Details of processed files should be kept in the repository (they can be expunged over time)
		// The backing repository will need additional configuration to expunge old entries
		// If using hazelcast the backing map can be configured with a max time to live for each key
		uri.set("idempotent", true)
			.set("idempotentRepository", "#" + inIdempotentRepositoryId)
			.set("inProgressRepository", "#" + inInProgressRepositoryId)
			.set("readLock", "idempotent");
		
		// Unknown files will be left in the folder (other application may be scanning for them)
		// Error control and data files will be moved to the error folder
		// Successfully processed control and data files are deleted as part of the target route
		// Moving of files is handled manually in the delegate route due to difficulties in disabling
		// the move option while using moveFailed
		uri.set("noop", true);
		
		from(uri.toString())
			.process(LOGGER.info(camelLogMsg("Received incoming DTS control file")
					.fileName(header(Exchange.FILE_NAME))))
		
			.convertBodyTo(ControlFile.class)

			.choice()
				// logging for unsupported control files
				.when(not(isToDTSKnown()))
					.process(LOGGER.info(camelLogMsg("Received DTS Data control file for unknown ToDTS mailbox - will not process")
							.fileName(header(Exchange.FILE_NAME))
							.workflowId("${body.getWorkflowId}")
							.fromDTS("${body.getFromDTS}")
							.toDTS("${body.getToDTS}")))
					.stop() // will not handle
				.endChoice()
				.when(not(isWorkflowIdKnown()))
					.process(LOGGER.info(camelLogMsg("Received DTS Data control file with unsupported workflowId - will not process")
						.fileName(header(Exchange.FILE_NAME))
						.workflowId("${body.getWorkflowId}")
						.fromDTS("${body.getFromDTS}")
						.toDTS("${body.getToDTS}")))
					.stop() // will not handle
				.endChoice()

				// main routing
				
				.when(and(isMessageType(MessageType.Report), isEvent(Event.SEND)))
					.to(dtsMessageSendNotificationReceiverUri)
				.endChoice()
				.when(and(isMessageType(MessageType.Data), isEvent(Event.TRANSFER)))
					.to(dtsMessageReceiverUri)
				.endChoice()

				// fallback - control file has supported worflow, but not a currently handled message type etc
				.otherwise()
					.process(LOGGER.info(camelLogMsg("Received DTS Data control file with unsupported messageType and/or event - will not process")
						.fileName(header(Exchange.FILE_NAME))
						.workflowId("${body.getWorkflowId}")
						.fromDTS("${body.getFromDTS}")
						.toDTS("${body.getToDTS}")))
					.stop() // will not handle
				.endChoice()
			.end()
		.end();
	}
	
	private Predicate isFromDTSKnown() {
		return new Predicate() {
			@Override
			public boolean matches(final Exchange exchange) {
				final ControlFile controlFile = exchange.getIn().getBody(ControlFile.class);
				return controlFile != null && mailboxes.contains(controlFile.getFromDTS());
			}
		};
	}
	
	private Predicate isToDTSKnown() {
		return new Predicate() {
			@Override
			public boolean matches(final Exchange exchange) {
				final ControlFile controlFile = exchange.getIn().getBody(ControlFile.class);
				return controlFile != null && mailboxes.contains(controlFile.getToDTS());
			}
		};
	}
	
	private Predicate isWorkflowIdKnown() {
		return new Predicate() {
			@Override
			public boolean matches(final Exchange exchange) {
				final ControlFile controlFile = exchange.getIn().getBody(ControlFile.class);
				return controlFile != null && workflowIds.contains(controlFile.getWorkflowId());
			}
		};
	}
	
	private static Predicate isEvent(final Event event) {
		return new Predicate() {
			@Override
			public boolean matches(final Exchange exchange) {
				final ControlFile controlFile = exchange.getIn().getBody(ControlFile.class);
				return controlFile != null && controlFile.getStatusRecord() != null &&
						controlFile.getStatusRecord().getEvent() == event;
			}
		};
	}
	
	private static Predicate isMessageType(final MessageType messageType) {
		return new Predicate() {
			@Override
			public boolean matches(final Exchange exchange) {
				final ControlFile controlFile = exchange.getIn().getBody(ControlFile.class);
				return controlFile != null && controlFile.getMessageType() == messageType;
			}
		};
	}
}
