package uk.nhs.ciao.transport.spine.route;

import com.google.common.base.Preconditions;

import uk.nhs.ciao.transport.spine.sds.EndpointAddress;
import uk.nhs.ciao.transport.spine.sds.EndpointAddressRepository;

/**
 * Creates a service route which enriches {@link EndpointAddress} using
 * the specified {@link EndpointAddressRepository}.
 */
public class EndpointAddressEnricherRoute extends BaseRouteBuilder {
	private String endpointAddressEnricherUrl;
	private EndpointAddressRepository endpointAddressRepository;
	
	public void setEndpointAddressEnricherUri(final String endpointAddressEnricherUrl) {
		this.endpointAddressEnricherUrl = endpointAddressEnricherUrl;
	}
	
	public void setEndpointAddressRepository(final EndpointAddressRepository endpointAddressRepository) {
		this.endpointAddressRepository = endpointAddressRepository;
	}
	
	@Override
	public void configure() throws Exception {
		Preconditions.checkNotNull(endpointAddressRepository, "endpointAddressServiceUrl is required");
		
		from(endpointAddressEnricherUrl)
			.setProperty("endpointAddress").body(EndpointAddress.class)
			.choice()
				.when().simple("${body.asid}")
					.bean(endpointAddressRepository, "findByAsid(${body.interaction}, ${body.asid})")
				.endChoice()
				.when().simple("${body.odsCode}")
					.bean(endpointAddressRepository, "findByODSCode(${body.interaction}, ${body.odsCode})")
				.endChoice()
			.end()
			
			.choice()
				.when().simple("${body} == null")
					.setBody().property("endpointAddress")
				.endChoice()
			.end()
			
			.removeProperty("endpointAddress")
			
		.end();
	}
}
