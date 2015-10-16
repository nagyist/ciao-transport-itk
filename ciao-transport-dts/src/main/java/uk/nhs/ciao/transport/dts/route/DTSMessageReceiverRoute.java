package uk.nhs.ciao.transport.dts.route;

import static uk.nhs.ciao.logging.CiaoCamelLogMessage.camelLogMsg;
import static uk.nhs.ciao.logging.CiaoLogMessage.logMsg;
import static uk.nhs.ciao.transport.dts.processor.DTSDataFilePoller.*;
import static uk.nhs.ciao.transport.dts.route.DTSHeaders.*;

import java.io.File;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.toolbox.AggregationStrategies;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;

import uk.nhs.ciao.camel.BaseRouteBuilder;
import uk.nhs.ciao.camel.URIBuilder;
import uk.nhs.ciao.dts.ControlFile;
import uk.nhs.ciao.logging.CiaoCamelLogger;
import uk.nhs.ciao.transport.dts.processor.DTSDataFilePoller;

/**
 * 
 * Route to receive control and data file pairs from the DTS IN folder and publish
 * the data payload.
 * <p>
 * File management of the IN directory is handled by this route.
 */
public class DTSMessageReceiverRoute extends BaseRouteBuilder {
	private static final CiaoCamelLogger LOGGER = CiaoCamelLogger.getLogger(DTSMessageReceiverRoute.class);
	
	private String dtsMessageReceiverUri;
	private String payloadDestinationUri;
	private String idempotentRepositoryId;
	private String inProgressRepositoryId;
	private String errorFolder;
	private final Set<String> workflowIds = Sets.newHashSet();
	
	// optional properties
	private long dataFilePollingInterval = 200;
	private int dataFileMaxAttempts = 100; // == 20 seconds
	
	/**
	 * URI where incoming DTS messages are received from
	 * <p>
	 * input only
	 */
	public void setDTSMessageReceiverUri(final String dtsMessageReceiverUri) {
		this.dtsMessageReceiverUri = dtsMessageReceiverUri;
	}
	
	/**
	 * URI where outgoing payload messages are sent to
	 * <p>
	 * output only
	 */
	public void setPayloadDestinationUri(final String payloadDestinationUri) {
		this.payloadDestinationUri = payloadDestinationUri;
	}
	
	/**
	 * Identifier of idempotent repository used to track incoming messages
	 */
	public void setIdempotentRepositoryId(final String idempotentRepositoryId) {
		this.idempotentRepositoryId = idempotentRepositoryId;
	}
	
	/**
	 * Identifier of idempotent repository to use to track in-progress incoming messages
	 */
	public void setInProgressRepositoryId(final String inProgressRepositoryId) {
		this.inProgressRepositoryId = inProgressRepositoryId;
	}
	
	/**
	 * Folder files should be moved to if an error occurs while processing
	 * <p>
	 * Only files intended for this application will be moved, other files will be left in the
	 * processing folder.
	 * 
	 * @see #setDTSFilePrefix(String)
	 */
	public void setErrorFolder(final String errorFolder) {
		this.errorFolder = errorFolder;
	}
	
	/**
	 * The set of DTS workflowIds this route handles.
	 * <p>
	 * Payload data files associated with these workflowIds will be published and the
	 * corresponding control and data files cleaned up.
	 */
	public void setWorflowIds(final Collection<String> workflowIds) {
		this.workflowIds.clear();
		this.workflowIds.addAll(workflowIds);
	}
	
	/**
	 * Time to wait between poll attempts when waiting for a DTS data file
	 */
	public void setDataFilePollingInterval(final int dataFilePollingInterval) {
		this.dataFilePollingInterval = dataFilePollingInterval;
	}
	
	/**
	 * Maximum polling attempts to make while waiting for a DTS data file
	 */
	public void setDataFileMaxAttempts(final int dataFileMaxAttempts) {
		this.dataFileMaxAttempts = dataFileMaxAttempts;
	}
	
	@Override
	public void configure() throws Exception {
		final ScheduledExecutorService executorService = getContext().getExecutorServiceManager()
				.newSingleThreadScheduledExecutor(this, "data-file-poller");
		
		// Additional configuration parameters are appended to the endpoint URI
		final URIBuilder uri = new URIBuilder(dtsMessageReceiverUri);
		
		// only handle control files
		uri.set("include", "..*\\.ctl");
		
		// only process each file once
		// Details of processed files should be kept in the repository (they can be expunged over time)
		// The backing repository will need additional configuration to expunge old entries
		// If using hazelcast the backing map can be configured with a max time to live for each key
		uri.set("idempotent", true)
			.set("idempotentRepository", "#" + idempotentRepositoryId)
			.set("inProgressRepository", "#" + inProgressRepositoryId)
			.set("readLock", "idempotent");
		
		// Unknown files will be left in the folder (other application may be scanning for them)
		// Error control and data files will be moved to the error folder
		// Successfully processed control and data files are deleted as part of the route
		// Moving of files is handled manually in the route due to difficulties in disabling
		// the move option while using moveFailed
		uri.set("noop", true);
		
		from(uri.toString())
			.onException(Exception.class)
				.process(new ControlFileAndDataFileMover())
			.end()
		
			.process(LOGGER.info(camelLogMsg("Received incoming DTS control file")
					.fileName(header(Exchange.FILE_NAME))))
			.setHeader("controlFileName").header(Exchange.FILE_NAME)
			.convertBodyTo(ControlFile.class)
			
			// Only handle control files for Data messages and known workflowIds
			.choice()
				.when(simple("${body.getMessageType} != ${type:uk.nhs.ciao.dts.MessageType.Data}"))
					.process(LOGGER.info(camelLogMsg("Received DTS Report control file - will not process")
							.fileName(header(Exchange.FILE_NAME))
							.workflowId(simple("${body.getWorkflowId}"))))
					.stop()
				.endChoice()
				.when(new ContainsUnsupportedWorkflowId())
					.process(LOGGER.info(camelLogMsg("Received DTS Data control file with unsupported workflowId - will not process")
							.fileName(header(Exchange.FILE_NAME))
							.workflowId(simple("${body.getWorkflowId}"))))
					.stop()
				.endChoice()
			.end()
			
			// Wait for the associated data file
			.setHeader(HEADER_DTS_FOLDER_NAME).header(Exchange.FILE_PARENT)
			.setHeader(HEADER_DATA_FILE_NAME, regexReplaceAll(
					simple("${header.CamelFileName}"), "(..*)\\.ctl", "$1.dat"))
			.process(new DTSDataFilePoller(executorService, dataFilePollingInterval, dataFileMaxAttempts))
			
			// Publish the payload (using multicast/pipeline to maintain original message)
			.multicast(AggregationStrategies.useOriginal())
				.pipeline()
					.setProperty("dtsControlFile").body(ControlFile.class)
					.setBody().header(HEADER_DATA_FILE)
					.convertBodyTo(byte[].class)
					
					// Store the control file properties - it may be required for future DTS exchanges (especially the workflowId)
					.removeHeaders("*")
					.setHeader(HEADER_WORKFLOW_ID).simple("${property.dtsControlFile.getWorkflowId}")
					.setHeader(HEADER_FROM_DTS).simple("${property.dtsControlFile.getFromDTS}")
					.setHeader(HEADER_TO_DTS).simple("${property.dtsControlFile.getToDTS}")
					.removeProperty("dtsControlFile")
					
					.to(payloadDestinationUri)
				.end()
			.end()
			
			// If successful delete the pair of files
			.process(new ControlFileAndDataFileDeleter())
		.end();
	}
	
	/**
	 * Tests if the control file contains a workflowId not handled by this receiver
	 */
	private class ContainsUnsupportedWorkflowId implements Predicate {
		@Override
		public boolean matches(final Exchange exchange) {
			final ControlFile controlFile = exchange.getIn().getBody(ControlFile.class);
			if (controlFile == null || Strings.isNullOrEmpty(controlFile.getWorkflowId())) {
				return false;
			}
			
			return !workflowIds.contains(controlFile.getWorkflowId());
		}
	}
	
	/**
	 * If successful, the control and data file are deleted
	 */
	private class ControlFileAndDataFileDeleter implements Processor {
		@Override
		public void process(final Exchange exchange) throws Exception {
			final File controlFile = exchange.getIn().getHeader(Exchange.FILE_PATH, File.class);
			final File dataFile = exchange.getIn().getHeader(DTSDataFilePoller.HEADER_DATA_FILE, File.class);
			
			FileUtil.deleteFile(dataFile);
			FileUtil.deleteFile(controlFile);
		}
	}
	
	/**
	 * If unsuccessful, the control file and data file (if present) is moved to the error folder
	 */
	private class ControlFileAndDataFileMover implements Processor {
		@Override
		public void process(final Exchange exchange) throws Exception {
			final File controlFile = exchange.getIn().getHeader(Exchange.FILE_PATH, File.class);
			moveToErrorFolder(controlFile);
			
			final File dataFile = exchange.getIn().getHeader(DTSDataFilePoller.HEADER_DATA_FILE, File.class);
			moveToErrorFolder(dataFile);
		}
		
		private void moveToErrorFolder(final File file) {
			if (file != null && file.isFile()) {
				try {
					final File sourceFolder = file.getParentFile();
					final File targetFolder = new File(sourceFolder, errorFolder);
					if (!targetFolder.exists()) {
						targetFolder.mkdirs();
					}
					
					final File target = new File(targetFolder, file.getName());
					
					final boolean copyAndDeleteOnRenameFail = true;
					FileUtil.renameFile(file, target, copyAndDeleteOnRenameFail);
				} catch (Exception e) {
					LOGGER.getCiaoLogger().warn(
							logMsg("Unable to move DTS file to error folder")
							.fileName(file.getName()), e);
				}
			}
			
		}
	}
}
