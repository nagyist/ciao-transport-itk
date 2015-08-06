package uk.nhs.ciao.transport.spine;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;

import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope;
import uk.nhs.ciao.transport.spine.route.DistributionEnvelopeSenderRoute;
import uk.nhs.ciao.transport.spine.route.ItkDocumentSenderRoute;
import uk.nhs.ciao.transport.spine.route.EbxmlAckReceiverRoute;
import uk.nhs.ciao.transport.spine.route.HttpServerRoute;
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
		
		addItkDocumentSenderRoute(context);
		addDistributionEnvelopeSenderRoute(context);
		addMultipartMessageSenderRoute(context);
		addHttpServerRoute(context);
		addEbxmlAckReceieverRoute(context);
	}
	
	private void addItkDocumentSenderRoute(final CamelContext context) throws Exception {
		final ItkDocumentSenderRoute route = new ItkDocumentSenderRoute();
		
		route.setDocumentSenderRouteUri("jms:queue:{{documentQueue}}?destination.consumer.prefetchSize=0");
		route.setDistributionEnvelopeSenderUri("direct:distribution-envelope-sender");
		
		context.addRoutes(route);
	}
	
	private void addDistributionEnvelopeSenderRoute(final CamelContext context) throws Exception {
		final DistributionEnvelopeSenderRoute route = new DistributionEnvelopeSenderRoute();
		
		route.setDistributionEnvelopeSenderUri("direct:distribution-envelope-sender");
		route.setMultipartMessageSenderUri("jms:queue:{{trunkRequestQueue}}");
		
		// TODO: Add/configure prototype objects to populate different parts of the message
		
		// Dummy prototypes for now!
		final EbxmlEnvelope ebxmlPrototype = new EbxmlEnvelope();
		ebxmlPrototype.setAction("COPC_IN000001GB01");
		route.setPrototypeEbxmlManifest(ebxmlPrototype);
		
		context.addRoutes(route);
	}
	
	private void addMultipartMessageSenderRoute(final CamelContext context) throws Exception {
		final MultipartMessageSenderRoute route = new MultipartMessageSenderRoute();
		
		route.setInternalRoutePrefix("trunk");
		route.setMultipartMessageSenderUri("jms:queue:{{trunkRequestQueue}}?destination.consumer.prefetchSize=0");
		route.setMultipartMessageDestinationUri("{{spine.toUri}}");
		route.setEbxmlAckReceiverUri("{{spine.replyUri}}");
		
		context.addRoutes(route);
	}
	
	private void addHttpServerRoute(final CamelContext context) throws Exception {
		final HttpServerRoute route = new HttpServerRoute();
		
		route.setHttpServerUrl("{{spine.fromUri}}");
		route.setEbxmlAckReceieverUrl("direct:asyncEbxmlAcks");
		route.setMultipartMessageReceieverUrl("direct:asyncItkAcks"); // Select a better route name
		
		context.addRoutes(route);
	}
	
	private void addEbxmlAckReceieverRoute(final CamelContext context) throws Exception {
		final EbxmlAckReceiverRoute route = new EbxmlAckReceiverRoute();
		
		route.setEbxmlAckReceiverUrl("direct:asyncEbxmlAcks");
		route.setEbxmlAckDestinationUrl("{{spine.replyUri}}");
		
		context.addRoutes(route);
	}
}
