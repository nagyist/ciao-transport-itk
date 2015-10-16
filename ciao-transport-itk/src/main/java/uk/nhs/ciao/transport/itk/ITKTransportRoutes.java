package uk.nhs.ciao.transport.itk;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.spi.IdempotentRepository;

import uk.nhs.ciao.camel.CamelApplication;
import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.docs.parser.route.InProgressFolderManagerRoute;
import uk.nhs.ciao.transport.itk.address.EndpointAddressHelper;
import uk.nhs.ciao.transport.itk.address.EndpointAddressRepository;
import uk.nhs.ciao.transport.itk.envelope.Address;
import uk.nhs.ciao.transport.itk.envelope.DistributionEnvelope;
import uk.nhs.ciao.transport.itk.envelope.Identity;
import uk.nhs.ciao.transport.itk.envelope.InfrastructureResponseFactory;
import uk.nhs.ciao.transport.itk.route.DistributionEnvelopeReceiverRoute;
import uk.nhs.ciao.transport.itk.route.DistributionEnvelopeSenderRoute;
import uk.nhs.ciao.transport.itk.route.EndpointAddressEnricherRoute;
import uk.nhs.ciao.transport.itk.route.ItkDocumentSenderRoute;
import uk.nhs.ciao.transport.itk.route.ItkMessageReceiverRoute;

public abstract class ITKTransportRoutes  implements RoutesBuilder {
	@Override
	public void addRoutesToCamelContext(final CamelContext context) throws Exception {
		// senders
		addItkDocumentSenderRoute(context);
		addDistributionEnvelopeSenderRoute(context);
		
		// receivers
		addDistributionEnvelopeReceiverRoute(context);
		addItkMessageReceiverRoute(context);
		
		// services
		addInProgressFolderManagerRoute(context);
		addEndpointAddressEnricherRoute(context);
	}
	
	private void addItkDocumentSenderRoute(final CamelContext context) throws Exception {
		final ItkDocumentSenderRoute route = new ItkDocumentSenderRoute();
		
		route.setDocumentSenderRouteUri("jms:queue:{{itkDocumentSenderQueue}}?destination.consumer.prefetchSize=0");
		route.setDistributionEnvelopeSenderUri("direct:distribution-envelope-sender");
		route.setDistributionEnvelopeResponseUri("direct:distribution-envelope-response");
		route.setInProgressFolderManagerUri("direct:in-progress-folder-manager");
		
		context.addRoutes(route);
	}
	
	protected abstract DistributionEnvelopeSenderRoute createDistributionEnvelopeSenderRoute(final CamelContext context,
			final CIAOConfig config) throws Exception;
	
	private void addDistributionEnvelopeSenderRoute(final CamelContext context) throws Exception {
		final CIAOConfig config = CamelApplication.getConfig(context);
		final DistributionEnvelopeSenderRoute route = createDistributionEnvelopeSenderRoute(context, config);
		
		route.setDistributionEnvelopeSenderUri("direct:distribution-envelope-sender");
		route.setDistributionEnvelopeResponseUri("direct:distribution-envelope-response");
		route.setEndpointAddressEnricherUri("direct:endpoint-address-enricher");

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
		
		context.addRoutes(route);
	}
	
	private void addDistributionEnvelopeReceiverRoute(final CamelContext context) throws Exception {
		final DistributionEnvelopeReceiverRoute route = new DistributionEnvelopeReceiverRoute();
		
		configureDistributionEnvelopeReceiverRoute(context, route);
		
		context.addRoutes(route);
	}
	
	protected void configureDistributionEnvelopeReceiverRoute(final CamelContext context, final DistributionEnvelopeReceiverRoute route) throws Exception {
		route.setDistributionEnvelopeReceiverUri(getDistributionEnvelopeReceiverUri() + "?destination.consumer.prefetchSize=0");
		route.setItkMessageReceiverUri("jms:queue:{{itkMessageReceiverQueue}}");
		route.setDistributionEnvelopeSenderUri("direct:distribution-envelope-sender");
		route.setIdempotentRepository(get(context, IdempotentRepository.class, "distributionEnvelopeIdempotentRepository"));
		route.setInfrastructureResponseFactory(new InfrastructureResponseFactory());
	}
	
	private void addItkMessageReceiverRoute(final CamelContext context) throws Exception {
		final ItkMessageReceiverRoute route = new ItkMessageReceiverRoute();
		
		route.setItkMessageReceiverUri("jms:queue:{{itkMessageReceiverQueue}}?destination.consumer.prefetchSize=0");
		route.setInProgressFolderManagerUri("direct:in-progress-folder-manager");
		
		context.addRoutes(route);
	}

	
	private void addInProgressFolderManagerRoute(final CamelContext context) throws Exception {
		final InProgressFolderManagerRoute route = new InProgressFolderManagerRoute();
		
		route.setInProgressFolderManagerUri("direct:in-progress-folder-manager");
		route.setInternalRoutePrefix("in-progress-folder-manager");
		route.setInProgressFolderRootUri("file:{{inProgressFolder}}");
		
		context.addRoutes(route);
	}
	
	@SuppressWarnings({"unchecked", "rawtypes"})
	private void addEndpointAddressEnricherRoute(final CamelContext context) throws Exception {
		final EndpointAddressEnricherRoute route = new EndpointAddressEnricherRoute();
		
		route.setEndpointAddressEnricherUri("direct:endpoint-address-enricher");
		
		final EndpointAddressRepository repository =
				context.getRegistry().lookupByNameAndType("endpointAddressRepository", EndpointAddressRepository.class);
		
		route.setHelper(createEndpointAddressHelper());
		route.setEndpointAddressRepository(repository);
		
		context.addRoutes(route);
	}
	
	protected String getDistributionEnvelopeReceiverUri() {
		return "jms:queue:{{distributionEnvelopeReceiverQueue}}";
	}
	
	protected abstract EndpointAddressHelper<?, ?> createEndpointAddressHelper();
	
	protected <T> T get(final CamelContext context, final Class<T> type, final String name) {
		return type.cast(context.getRegistry().lookupByName(name));
	}
}
