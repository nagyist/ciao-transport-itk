package uk.nhs.ciao.transport.dts;

import java.util.Set;

import org.apache.camel.CamelContext;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;

import uk.nhs.ciao.camel.CamelApplication;
import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.dts.ControlFile;
import uk.nhs.ciao.transport.dts.address.DTSEndpointAddressHelper;
import uk.nhs.ciao.transport.dts.route.DTSDistributionEnvelopeSenderRoute;
import uk.nhs.ciao.transport.dts.route.DTSMessageReceiverRoute;
import uk.nhs.ciao.transport.dts.route.DTSResponseDetailsInjectorRoute;
import uk.nhs.ciao.transport.dts.sequence.IdGenerator;
import uk.nhs.ciao.transport.itk.ITKTransportRoutes;
import uk.nhs.ciao.transport.itk.address.EndpointAddressHelper;
import uk.nhs.ciao.transport.itk.route.DistributionEnvelopeReceiverRoute;
import uk.nhs.ciao.transport.itk.route.DistributionEnvelopeSenderRoute;

public class DTSTransportRoutes extends ITKTransportRoutes {
	@Override
	public void addRoutesToCamelContext(CamelContext context) throws Exception {
		super.addRoutesToCamelContext(context);
		
		// Receivers
		addDTSMessageReceiverRoute(context);
	}
	
	@Override
	protected DistributionEnvelopeSenderRoute createDistributionEnvelopeSenderRoute(
			final CamelContext context, final CIAOConfig config) throws Exception {
		final DTSDistributionEnvelopeSenderRoute route = new DTSDistributionEnvelopeSenderRoute();
		
		route.setDTSMessageSenderUri(context.resolvePropertyPlaceholders("file://{{dts.rootFolder}}/OUT"));
		route.setDTSMessageSendNotificationReceiverUri(context.resolvePropertyPlaceholders("file://{{dts.rootFolder}}/SENT"));
		route.setDTSTemporaryFolder(context.resolvePropertyPlaceholders("{{dts.temporaryFolder}}"));
		route.setDTSFilePrefix(Strings.nullToEmpty(config.getConfigValue("dts.filePrefix")));
		route.setIdempotentRepositoryId("dtsSentIdempotentRepository");
		route.setInProgressRepositoryId("dtsSentInProgressRepository");
		route.setIdGenerator(get(context, IdGenerator.class, "dtsIdGenerator"));
		
		final ControlFile prototype = new ControlFile();
		prototype.setWorkflowId(config.getConfigValue("dts.workflowId"));
		prototype.setFromDTS(config.getConfigValue("dts.senderMailbox"));
		route.setPrototypeControlFile(prototype);
		
		return route;
	}
	
	@Override
	protected EndpointAddressHelper<?, ?> createEndpointAddressHelper() {
		return new DTSEndpointAddressHelper();
	}
	
	private void addDTSMessageReceiverRoute(final CamelContext context) throws Exception {
		final CIAOConfig config = CamelApplication.getConfig(context);
		final DTSMessageReceiverRoute route = new DTSMessageReceiverRoute();
		
		route.setDTSMessageReceiverUri(context.resolvePropertyPlaceholders("file://{{dts.rootFolder}}/IN"));
		route.setErrorFolder(context.resolvePropertyPlaceholders("{{dts.errorFolder}}"));
		route.setPayloadDestinationUri(getDistributionEnvelopeReceiverUri());
		route.setIdempotentRepositoryId("dtsReceiverIdempotentRepository");
		route.setInProgressRepositoryId("dtsReceiverInProgressRepository");
		route.setMailbox(config.getConfigValue("dts.senderMailbox"));
		
		final Set<String> workflowIds = Sets.newHashSet();
		for (final String workflowId: config.getConfigValue("dts.receiverWorkflowIds").split(",")) {
			if (!workflowId.trim().isEmpty()) {
				workflowIds.add(workflowId);
			}
		}
		
		route.setWorflowIds(workflowIds);
		
		context.addRoutes(route);
	}
	
	/**
	 * Updated default behaviour to inject the DTS workflow details into outgoing responses
	 * @throws Exception 
	 */
	@Override
	protected void configureDistributionEnvelopeReceiverRoute(final CamelContext context, final DistributionEnvelopeReceiverRoute route) throws Exception {
		super.configureDistributionEnvelopeReceiverRoute(context, route);
		
		// have the injector pass the updated messages on to the original target route
		final DTSResponseDetailsInjectorRoute injectorRoute = new DTSResponseDetailsInjectorRoute();
		injectorRoute.setFromDistributionEnvelopeReceiverUri("direct:dts-workflow-details-injector");
		injectorRoute.setToDistributionEnvelopeSenderUri(route.getDistributionEnvelopeSenderUri());
		
		// Update the sender to route it's messages through the injector
		route.setDistributionEnvelopeSenderUri("direct:dts-workflow-details-injector");
		
		context.addRoutes(injectorRoute);
	}
}
