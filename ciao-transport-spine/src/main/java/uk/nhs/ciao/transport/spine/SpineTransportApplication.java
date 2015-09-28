package uk.nhs.ciao.transport.spine;

import uk.nhs.ciao.camel.CamelApplication;
import uk.nhs.ciao.camel.CamelApplicationRunner;
import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.exceptions.CIAOConfigurationException;

/**
 * The main ciao-cda-builder application
 */
public class SpineTransportApplication extends CamelApplication {
	/**
	 * Runs the CDA builder application
	 * 
	 * @see CIAOConfig#CIAOConfig(String[], String, String, java.util.Properties)
	 * @see CamelApplicationRunner
	 */
	public static void main(final String[] args) throws Exception {
		final CamelApplication application = new SpineTransportApplication(args);
		CamelApplicationRunner.runApplication(application);
	}
	
	public SpineTransportApplication(final String... args) throws CIAOConfigurationException {
		super("ciao-transport-spine.properties", args);
	}
	
	public SpineTransportApplication(final CIAOConfig ciaoConfig, final String... args) {
		super(ciaoConfig, args);
	}
}
