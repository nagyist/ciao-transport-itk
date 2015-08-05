package uk.nhs.ciao.transport.spine;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;

import uk.nhs.ciao.transport.spine.route.LegacySpineTransportRoutes;
import uk.nhs.ciao.transport.spine.route.MultipartMessageSenderRoute;

/**
 * Main routes builder for the spine transport
 * <p>
 * Configures and adds delegate RouteBuilder instances based on
 * the CIAOProperties configuration
 */
public class SpineTransportRoutes implements RoutesBuilder {
	@Override
	public void addRoutesToCamelContext(final CamelContext context) throws Exception {
		context.addRoutes(new LegacySpineTransportRoutes());
		
		addMultipartMessageSenderRoute(context);
	}
	
	private void addMultipartMessageSenderRoute(final CamelContext context) throws Exception {
		final MultipartMessageSenderRoute route = new MultipartMessageSenderRoute();
		
		route.setInternalRoutePrefix("trunk");
		route.setMultipartMessageSenderUri("spine:trunk");
		route.setMultipartMessageDestinationUri("{{spine.toUri}}");
		route.setEbxmlAckReceiverUri("{{spine.replyUri}}");
		
		context.addRoutes(route);
	}
}
