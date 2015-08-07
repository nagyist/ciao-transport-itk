package uk.nhs.ciao.transport.spine;

import java.io.File;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope;
import uk.nhs.ciao.transport.spine.route.DistributionEnvelopeSenderRoute;
import uk.nhs.ciao.transport.spine.route.ItkDocumentSenderRoute;
import uk.nhs.ciao.transport.spine.route.EbxmlAckReceiverRoute;
import uk.nhs.ciao.transport.spine.route.HttpServerRoute;
import uk.nhs.ciao.transport.spine.route.MultipartMessageSenderRoute;
import uk.nhs.ciao.transport.spine.route.EndpointAddressEnricherRoute;
import uk.nhs.ciao.transport.spine.sds.MemoryEndpointAddressRepository;
import uk.nhs.ciao.transport.spine.sds.EndpointAddress;
import uk.nhs.ciao.transport.spine.util.GenericJacksonDataFormat;

/**
 * Main routes builder for the spine transport
 * <p>
 * Configures and adds delegate RouteBuilder instances based on
 * the CIAOProperties configuration
 */
public class SpineTransportRoutes implements RoutesBuilder {
	@Override
	public void addRoutesToCamelContext(final CamelContext context) throws Exception {
		// senders
		addItkDocumentSenderRoute(context);
		addDistributionEnvelopeSenderRoute(context);
		addMultipartMessageSenderRoute(context);
		
		// receivers
		addHttpServerRoute(context);
		addEbxmlAckReceieverRoute(context);
		addMultipartMessageReceiverRoute(context);
		addDistributionEnvelopeReceiverRoute(context);
		addItkMessageReceiverRoute(context);
		
		// services
		addEndpointAddressServiceRoute(context);
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
		route.setEndpointAddressEnricherUri("direct:endpoint-address-enricher");
		
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
		// TODO:
	}
	
	private void addDistributionEnvelopeReceiverRoute(final CamelContext context) throws Exception {
		// TODO:
	}
	
	private void addItkMessageReceiverRoute(final CamelContext context) throws Exception {
		// TODO:
	}
	
	private void addEndpointAddressServiceRoute(final CamelContext context) throws Exception {
		final EndpointAddressEnricherRoute route = new EndpointAddressEnricherRoute();
		
		route.setEndpointAddressEnricherUri("direct:endpoint-address-enricher");
		
		// TODO: Work out how to configure this
		final MemoryEndpointAddressRepository repository = new MemoryEndpointAddressRepository();
		route.setEndpointAddressRepository(repository);
		
		
		// There is a race condition during startup here
//		context.addRoutes(new RouteBuilder() {
//			@Override
//			public void configure() throws Exception {
//				from("file:./?fileName=endpoint-addresses.json&initialDelay=0")
//					.unmarshal(new GenericJacksonDataFormat(new TypeReference<List<EndpointAddress>>() {}))
//					.bean(repository, "storeAll")
//				.end();
//			}
//		});
		
		// For now - load the JSON manually (if available)
		final File file = new File("endpoint-addresses.json");
		if (file.isFile()) {
			final ObjectMapper mapper = new ObjectMapper();
			final List<EndpointAddress> endpointAddresses = mapper.readValue(file, new TypeReference<List<EndpointAddress>>() {});
			repository.storeAll(endpointAddresses);
		}
		
		context.addRoutes(route);
	}
}
