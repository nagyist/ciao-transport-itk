package uk.nhs.ciao.transport.dts.processor;

import static uk.nhs.ciao.logging.CiaoLogMessage.logMsg;

import java.io.File;
import java.io.IOException;

import org.apache.camel.util.FileUtil;

import uk.nhs.ciao.logging.CiaoLogger;

import com.google.common.base.Strings;

/**
 * Utility to perform housekeeping on DTS files/folders after handling
 */
public class DTSFileHousekeeper {
	private static final CiaoLogger LOGGER = CiaoLogger.getLogger(DTSFileHousekeeper.class);
	
	private String destinationFolder;
	
	public DTSFileHousekeeper() {
		// NOOP
	}
	
	public DTSFileHousekeeper(final String destinationFolder) {
		this.destinationFolder = destinationFolder;
	}
	
	/**
	 * Sets the destination folder to move files to
	 * <p>
	 * If null, the files are deleted
	 */
	public void setDestinationFolder(final String destinationFolder) {
		this.destinationFolder = destinationFolder;
	}
	
	/**
	 * Perform housekeeping / cleanup on the specified DTS file
	 */
	public void cleanup(final File file) {
		if (file == null || !file.isFile()) {
			// Nothing to do
		}
		
		// delete or move
		if (Strings.isNullOrEmpty(destinationFolder)) {
			if (!FileUtil.deleteFile(file)) {
				LOGGER.warn(
						logMsg("Unable to delete DTS file")
						.fileName(file.getName()));
			}
		} else {
			final File folder = new File(file.getParent(), destinationFolder);
			if (!folder.exists()) {
				folder.mkdirs();
			}
			
			final File destination = new File(folder, file.getName());
			try {
				final boolean copyAndDeleteOnRenameFail = true;
				FileUtil.renameFile(file, destination, copyAndDeleteOnRenameFail);
			} catch (IOException e) {
				LOGGER.warn(
						logMsg("Unable to move DTS file to housekeeping folder")
						.fileName(file.getName()), e);
			}
		}
	}
}
