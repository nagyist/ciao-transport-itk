package uk.nhs.ciao.transport.dts.route;

/**
 * DTS-specific headers added to messages
 */
public final class DTSHeaders {
	/**
	 * The worflow ID of the message
	 */
	public static final String HEADER_WORKFLOW_ID = "dtsWorkflowId";
	
	/**
	 * The from DTS mailbox of the message
	 */
	public static final String HEADER_FROM_DTS = "dtsFromDTS";
	
	/**
	 * The to DTS mailbox of the message
	 */
	public static final String HEADER_TO_DTS = "dtsToDTS";
	
	private DTSHeaders() {
		// Suppress default constructor
	}
}
