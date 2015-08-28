package uk.nhs.ciao.transport.spine;

import java.io.File;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.spi.IdempotentRepository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import uk.nhs.ciao.camel.CamelApplication;
import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.transport.spine.address.MemorySpineEndpointAddressRepository;
import uk.nhs.ciao.transport.spine.address.SpineEndpointAddress;
import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope;
import uk.nhs.ciao.transport.spine.hl7.HL7Part;
import uk.nhs.ciao.transport.spine.itk.Address;
import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope;
import uk.nhs.ciao.transport.spine.itk.Identity;
import uk.nhs.ciao.transport.spine.itk.InfrastructureResponseFactory;
import uk.nhs.ciao.transport.spine.route.DistributionEnvelopeReceiverRoute;
import uk.nhs.ciao.transport.spine.route.DistributionEnvelopeSenderRoute;
import uk.nhs.ciao.transport.spine.route.InProgressFolderManagerRoute;
import uk.nhs.ciao.transport.spine.route.ItkDocumentSenderRoute;
import uk.nhs.ciao.transport.spine.route.EbxmlAckReceiverRoute;
import uk.nhs.ciao.transport.spine.route.HttpServerRoute;
import uk.nhs.ciao.transport.spine.route.ItkMessageReceiverRoute;
import uk.nhs.ciao.transport.spine.route.MultipartMessageReceiverRoute;
import uk.nhs.ciao.transport.spine.route.MultipartMessageSenderRoute;
import uk.nhs.ciao.transport.spine.route.SpineEndpointAddressEnricherRoute;

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
		addSpineEndpointAddressEnricherRoute(context);
		addInProgressFolderManagerRoute(context);
	}
	
	private void addItkDocumentSenderRoute(final CamelContext context) throws Exception {
		final ItkDocumentSenderRoute route = new ItkDocumentSenderRoute();
		
		route.setDocumentSenderRouteUri("jms:queue:{{itkDocumentSenderQueue}}?destination.consumer.prefetchSize=0");
		route.setDistributionEnvelopeSenderUri("direct:distribution-envelope-sender");
		route.setInProgressFolderManagerUri("direct:in-progress-folder-manager");
		
		context.addRoutes(route);
	}
	
	private void addDistributionEnvelopeSenderRoute(final CamelContext context) throws Exception {
		final DistributionEnvelopeSenderRoute route = new DistributionEnvelopeSenderRoute();
		
		route.setDistributionEnvelopeSenderUri("direct:distribution-envelope-sender");
		route.setMultipartMessageSenderUri("jms:queue:{{multipartMessageSenderQueue}}");
		route.setSpineEndpointAddressEnricherUri("direct:spine-endpoint-address-enricher");
		
		final CIAOConfig config = CamelApplication.getConfig(context);
		
		final DistributionEnvelope distributionEnvelopePrototype = new DistributionEnvelope();
		distributionEnvelopePrototype.setService(config.getConfigValue("senderItkService"));
		
		final Address senderAddress = new Address();
		senderAddress.setODSCode(String.valueOf(config.getConfigValue("senderODSCode")));
		distributionEnvelopePrototype.setSenderAddress(senderAddress);
		
		final Identity auditIdentity = new Identity();
		if (config.getConfigKeys().contains("auditODSCode")) {
			auditIdentity.setODSCode(String.valueOf(config.getConfigValue("auditODSCode")));
		} else {
			auditIdentity.setODSCode(String.valueOf(config.getConfigValue("senderODSCode")));
		}
		distributionEnvelopePrototype.setAuditIdentity(auditIdentity);
		
		route.setPrototypeDistributionEnvelope(distributionEnvelopePrototype);
		
		final EbxmlEnvelope ebxmlPrototype = new EbxmlEnvelope();
		ebxmlPrototype.setService(config.getConfigValue("senderService"));
		ebxmlPrototype.setAction(config.getConfigValue("senderAction"));
		ebxmlPrototype.setFromParty(config.getConfigValue("senderPartyId"));
		route.setPrototypeEbxmlManifest(ebxmlPrototype);
		
		final HL7Part hl7Prototype = new HL7Part();
		hl7Prototype.setSenderAsid(config.getConfigValue("senderAsid"));
		route.setPrototypeHl7Part(hl7Prototype);
		
		context.addRoutes(route);
	}
	
	private void addMultipartMessageSenderRoute(final CamelContext context) throws Exception {
		final MultipartMessageSenderRoute route = new MultipartMessageSenderRoute();
		
		route.setInternalRoutePrefix("multipart-message-sender");
		route.setMultipartMessageSenderUri("jms:queue:{{multipartMessageSenderQueue}}?destination.consumer.prefetchSize=0");
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
		final MultipartMessageReceiverRoute route = new MultipartMessageReceiverRoute();
		
		route.setMultipartReceiverUri("direct:multipart-message-receiever");
		route.setPayloadDestinationUri("jms:queue:{{distributionEnvelopeReceiverQueue}}");
		route.setEbxmlResponseDestinationUri("{{spine.toUri}}");
		route.setIdempotentRepository(get(context, IdempotentRepository.class, "multipartMessageIdempotentRepository"));
		
		context.addRoutes(route);
	}
	
	private void addDistributionEnvelopeReceiverRoute(final CamelContext context) throws Exception {
		final DistributionEnvelopeReceiverRoute route = new DistributionEnvelopeReceiverRoute();
		
		route.setDistributionEnvelopeReceiverUri("jms:queue:{{distributionEnvelopeReceiverQueue}}?destination.consumer.prefetchSize=0");
		route.setItkMessageReceiverUri("jms:queue:{{itkMessageReceiverQueue}}");
		route.setDistributionEnvelopeSenderUri("direct:distribution-envelope-sender");
		route.setIdempotentRepository(get(context, IdempotentRepository.class, "distributionEnvelopeIdempotentRepository"));
		route.setInfrastructureResponseFactory(new InfrastructureResponseFactory());
		
		context.addRoutes(route);
	}
	
	private void addItkMessageReceiverRoute(final CamelContext context) throws Exception {
		final ItkMessageReceiverRoute route = new ItkMessageReceiverRoute();
		
		route.setItkMessageReceiverUri("jms:queue:{{itkMessageReceiverQueue}}?destination.consumer.prefetchSize=0");
		route.setInProgressFolderManagerUri("direct:in-progress-folder-manager");
		
		context.addRoutes(route);
	}
	
	private void addSpineEndpointAddressEnricherRoute(final CamelContext context) throws Exception {
		final SpineEndpointAddressEnricherRoute route = new SpineEndpointAddressEnricherRoute();
		
		route.setSpineEndpointAddressEnricherUri("direct:spine-endpoint-address-enricher");
		
		// TODO: Work out how to configure this
		final MemorySpineEndpointAddressRepository repository = new MemorySpineEndpointAddressRepository();
		route.setSpineEndpointAddressRepository(repository);
		
		// For now - load the JSON manually (if available)
		final File file = new File("endpoint-addresses.json");
		if (file.isFile()) {
			final ObjectMapper mapper = new ObjectMapper();
			final List<SpineEndpointAddress> spineEndpointAddresses = mapper.readValue(file, new TypeReference<List<SpineEndpointAddress>>() {});
			repository.storeAll(spineEndpointAddresses);
		}
		
		context.addRoutes(route);
	}
	
	private void addInProgressFolderManagerRoute(final CamelContext context) throws Exception {
		final InProgressFolderManagerRoute route = new InProgressFolderManagerRoute();
		
		route.setInProgressFolderManagerUri("direct:in-progress-folder-manager");
		route.setInternalRoutePrefix("in-progress-folder-manager");
		route.setInProgressFolderRootUri("file:{{inProgressFolder}}");
		
		context.addRoutes(route);
	}
	
	private <T> T get(final CamelContext context, final Class<T> type, final String name) {
		return type.cast(context.getRegistry().lookupByName(name));
	}
}
