package uk.nhs.ciao.transport.spine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.nhs.ciao.CIPRoutes;
import uk.nhs.ciao.camel.CamelApplication;
import uk.nhs.ciao.configuration.CIAOConfig;

/**
 * Configures multiple camel CDA builder routes determined by properties specified
 * in the applications registered {@link CIAOConfig}.
 */
@SuppressWarnings("unused")
public class SpineTransportRoutes extends CIPRoutes {
	private static final Logger LOGGER = LoggerFactory.getLogger(SpineTransportRoutes.class);
	
	/**
	 * The root property 
	 */
	public static final String ROOT_PROPERTY = "cdaBuilderRoutes";
	
	/**
	 * Creates multiple document parser routes
	 * 
	 * @throws RuntimeException If required CIAO-config properties are missing
	 */
	@Override
	public void configure() {
		super.configure();
		
		final CIAOConfig config = CamelApplication.getConfig(getContext());
		// TODO: Complete routes
		
//		try {
//			
//		} catch (CIAOConfigurationException e) {
//			throw new RuntimeException("Unable to build routes from CIAOConfig", e);
//		}
	}
}
