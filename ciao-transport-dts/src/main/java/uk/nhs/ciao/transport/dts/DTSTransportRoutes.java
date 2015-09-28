package uk.nhs.ciao.transport.dts;

import org.apache.camel.CamelContext;

import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.transport.dts.route.DTSDistributionEnvelopeSenderRoute;
import uk.nhs.ciao.transport.itk.ITKTransportRoutes;
import uk.nhs.ciao.transport.itk.route.DistributionEnvelopeSenderRoute;

public class DTSTransportRoutes extends ITKTransportRoutes {
	
	@Override
	protected DistributionEnvelopeSenderRoute createDistributionEnvelopeSenderRoute(
			final CamelContext context, final CIAOConfig config) throws Exception {
		final DTSDistributionEnvelopeSenderRoute route = new DTSDistributionEnvelopeSenderRoute();
		
		// TODO: Complete this for DTS - code below is from spine route
//		route.setDTSMessageSenderUri(dtsMessageSenderUri);
//		route.setDTSMessageSendResponseReceiverUri(dtsMessageSendResponseReceiverUri);
		
//		route.setMultipartMessageSenderUri("jms:queue:{{multipartMessageSenderQueue}}");
//		route.setMultipartMessageResponseUri("jms:queue:{{multipartMessageResponseQueue}}?destination.consumer.prefetchSize=0");
//		route.setSpineEndpointAddressEnricherUri("direct:spine-endpoint-address-enricher");
		
//		final EbxmlEnvelope ebxmlPrototype = new EbxmlEnvelope();
//		ebxmlPrototype.setService(config.getConfigValue("senderService"));
//		ebxmlPrototype.setAction(config.getConfigValue("senderAction"));
//		ebxmlPrototype.setFromParty(config.getConfigValue("senderPartyId"));
//		route.setPrototypeEbxmlManifest(ebxmlPrototype);
//		
//		final HL7Part hl7Prototype = new HL7Part();
//		hl7Prototype.setSenderAsid(config.getConfigValue("senderAsid"));
//		route.setPrototypeHl7Part(hl7Prototype);
		
		return route;
	}
}
