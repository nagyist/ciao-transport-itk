package uk.nhs.ciao.transport.dts;

import uk.nhs.ciao.camel.CamelApplication;
import uk.nhs.ciao.camel.CamelApplicationRunner;
import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.exceptions.CIAOConfigurationException;

/**
 * The main ciao-transport-dts application
 */
public class DTSTransportApplication extends CamelApplication {
	/**
	 * Runs the DTS-transport application
	 * 
	 * @see CIAOConfig#CIAOConfig(String[], String, String, java.util.Properties)
	 * @see CamelApplicationRunner
	 */
	public static void main(final String[] args) throws Exception {
		final CamelApplication application = new DTSTransportApplication(args);
		CamelApplicationRunner.runApplication(application);
	}
	
	public DTSTransportApplication(final String... args) throws CIAOConfigurationException {
		super("ciao-transport-dts.properties", args);
	}
	
	public DTSTransportApplication(final CIAOConfig ciaoConfig, final String... args) {
		super(ciaoConfig, args);
	}
}
