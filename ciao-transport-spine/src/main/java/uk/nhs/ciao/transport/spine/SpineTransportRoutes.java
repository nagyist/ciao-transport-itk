package uk.nhs.ciao.transport.spine;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.IdempotentRepository;

import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.transport.itk.ITKTransportRoutes;
import uk.nhs.ciao.transport.itk.address.EndpointAddressHelper;
import uk.nhs.ciao.transport.spine.address.SpineEndpointAddressHelper;
import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope;
import uk.nhs.ciao.transport.spine.hl7.HL7Part;
import uk.nhs.ciao.transport.spine.route.EbxmlAckReceiverRoute;
import uk.nhs.ciao.transport.spine.route.HttpServerRoute;
import uk.nhs.ciao.transport.spine.route.MultipartMessageReceiverRoute;
import uk.nhs.ciao.transport.spine.route.MultipartMessageSenderRoute;
import uk.nhs.ciao.transport.spine.route.SpineDistributionEnvelopeSenderRoute;

/**
 * Main routes builder for the spine transport
 * <p>
 * Configures and adds delegate RouteBuilder instances based on
 * the CIAOProperties configuration
 */
public class SpineTransportRoutes extends ITKTransportRoutes {
	@Override
	public void addRoutesToCamelContext(final CamelContext context) throws Exception {
		super.addRoutesToCamelContext(context);
		
		// senders
		addMultipartMessageSenderRoute(context);
		
		// receivers
		addHttpServerRoute(context);
		addEbxmlAckReceieverRoute(context);
		addMultipartMessageReceiverRoute(context);
	}
	
	@Override
	protected SpineDistributionEnvelopeSenderRoute createDistributionEnvelopeSenderRoute(
			final CamelContext context, final CIAOConfig config) throws Exception {
		final SpineDistributionEnvelopeSenderRoute route = new SpineDistributionEnvelopeSenderRoute();
		
		route.setMultipartMessageSenderUri("jms:queue:{{multipartMessageSenderQueue}}");
		route.setMultipartMessageResponseUri("jms:queue:{{multipartMessageResponseQueue}}?destination.consumer.prefetchSize=0");
		route.setEndpointAddressEnricherUri(getEndpointAddressEnricherUri());
		
		final EbxmlEnvelope ebxmlPrototype = new EbxmlEnvelope();
		ebxmlPrototype.setService(config.getConfigValue("senderService"));
		ebxmlPrototype.setAction(config.getConfigValue("senderAction"));
		ebxmlPrototype.setFromParty(config.getConfigValue("senderPartyId"));
		route.setPrototypeEbxmlManifest(ebxmlPrototype);
		
		final HL7Part hl7Prototype = new HL7Part();
		hl7Prototype.setSenderAsid(config.getConfigValue("senderAsid"));
		route.setPrototypeHl7Part(hl7Prototype);
		
		return route;
	}
	
	private void addMultipartMessageSenderRoute(final CamelContext context) throws Exception {
		final MultipartMessageSenderRoute route = new MultipartMessageSenderRoute();
		
		route.setInternalRoutePrefix("multipart-message-sender");
		route.setMultipartMessageSenderUri("jms:queue:{{multipartMessageSenderQueue}}?destination.consumer.prefetchSize=0");
		route.setMultipartMessageDestinationUri("{{spine.toUri}}");
		route.setEbxmlAckReceiverUri("{{spine.replyUri}}");
		route.setMultipartMessageResponseUri("jms:queue:{{multipartMessageResponseQueue}}");
		
		context.addRoutes(route);
	}
	
	private void addHttpServerRoute(final CamelContext context) throws Exception {
		final HttpServerRoute route = new HttpServerRoute();
		
		route.setHttpServerUrl("{{spine.fromUri}}");
		route.setEbxmlAckReceiverUrl("direct:ebxml-ack-receiver");
		route.setMultipartMessageReceiverUrl("direct:multipart-message-receiever");
		
		context.addRoutes(route);
	}
	
	private void addEbxmlAckReceieverRoute(final CamelContext context) throws Exception {
		final EbxmlAckReceiverRoute route = new EbxmlAckReceiverRoute();
		
		route.setEbxmlAckReceiverUrl("direct:ebxml-ack-receiver");
		route.setEbxmlAckDestinationUrl("{{spine.replyUri}}");
		
		context.addRoutes(route);
	}
	
	private void addMultipartMessageReceiverRoute(final CamelContext context) throws Exception {
		final MultipartMessageReceiverRoute route = new MultipartMessageReceiverRoute();
		
		route.setMultipartReceiverUri("direct:multipart-message-receiever");
		route.setPayloadDestinationUri("jms:queue:{{distributionEnvelopeReceiverQueue}}");
		route.setEbxmlResponseDestinationUri("{{spine.toUri}}");
		route.setIdempotentRepository(get(context, IdempotentRepository.class, "multipartMessageIdempotentRepository"));
		
		context.addRoutes(route);
	}
	
	@Override
	protected EndpointAddressHelper<?, ?> createEndpointAddressHelper() {
		return new SpineEndpointAddressHelper();
	}
}
