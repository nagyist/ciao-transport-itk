package uk.nhs.ciao.transport.dts.processor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.util.AsyncProcessorHelper;

import uk.nhs.ciao.logging.CiaoLogMessage;
import uk.nhs.ciao.logging.CiaoLogger;

import com.google.common.base.Preconditions;

/**
 * Asynchronous poller that checks for the existence of a named DTS data file.
 * <p>
 * The poller attempts 
 */
public class DTSDataFilePoller implements AsyncProcessor {
	private static final CiaoLogger LOGGER = CiaoLogger.getLogger(DTSDataFilePoller.class);
	
	/**
	 * Header containing the name of the file to find
	 */
	public static final String HEADER_FOLDER_NAME = "dtsFolderName";
	
	/**
	 * Header containing the name of the file to find
	 */
	public static final String HEADER_FILE_NAME = "dataFileName";
	
	/**
	 * Header containing the file (once found)
	 */
	public static final String HEADER_FILE = "dataFile";
	
	private final ScheduledExecutorService executorService;
	private final long pollingInterval;
	private final int maxAttempts;
	
	/**
	 * Constructs a new poller
	 * 
	 * @param executorService Service used for async execution
	 * @param pollingInterval Time to wait between poll attempts (in millis)
	 * @param maxAttempts Maximum polling attempts to try
	 */
	public DTSDataFilePoller(final ScheduledExecutorService executorService,
			final long pollingInterval, final int maxAttempts) {
		this.executorService = Preconditions.checkNotNull(executorService, "executorService");
		this.pollingInterval = pollingInterval;
		this.maxAttempts = maxAttempts;
		
		Preconditions.checkArgument(pollingInterval >= 0, "pollingInterval must not be negative");
		Preconditions.checkArgument(maxAttempts > 0, "maxAttempts must be positive");
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void process(final Exchange exchange) throws Exception {
		AsyncProcessorHelper.process(this, exchange);
	}
	
	/**
	 * Polls for the file named in the input header: {@link #HEADER_FILE_NAME}.
	 * <p>
	 * If the file is found it will be stored in the input header: {@link #HEADER_FILE},
	 * otherwise a {@link FileNotFoundException} will be returned in the exchange.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean process(final Exchange exchange, final AsyncCallback callback) {
		final File folder = exchange.getIn().getHeader(HEADER_FOLDER_NAME, File.class);
		final String fileName = exchange.getIn().getHeader(HEADER_FILE_NAME, String.class);
		final File file = new File(folder, fileName);
		
		LOGGER.info(CiaoLogMessage.logMsg("Waiting for DTS data file").fileName(fileName));
		
		class PollForFileTask implements Runnable {
			private volatile int attempt;
			
			@Override
			public void run() {
				final boolean isSync = false;
				run(isSync);
			}
			
			public boolean run(final boolean isSync) {
				try {
					attempt++; // safe - only one thread runs concurrently
					
					if (file.exists()) {
						LOGGER.info(CiaoLogMessage.logMsg("Succesfully found DTS data file").fileName(fileName));
						exchange.getIn().setHeader(HEADER_FILE, file);
					} else if (attempt >= maxAttempts) {
						final String message = "DTS data file could not be found - maximum attempts exceeded";
						LOGGER.info(CiaoLogMessage.logMsg(message).fileName(fileName));
						exchange.setException(new FileNotFoundException(message + ": " + file.getPath()));
					} else {
						executorService.schedule(this, pollingInterval, TimeUnit.MILLISECONDS);
						return false;
					}						
				} catch (Exception e) {
					exchange.setException(e);
				}
				
				if (!isSync) {
					callback.done(isSync);
				}
				
				return true;
			}
		}
		
		final boolean isSync = true;
		return new PollForFileTask().run(isSync);
	}
}