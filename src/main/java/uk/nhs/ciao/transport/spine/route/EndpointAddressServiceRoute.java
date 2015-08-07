package uk.nhs.ciao.transport.spine.route;

import com.google.common.base.Preconditions;

import uk.nhs.ciao.transport.spine.sds.EndpointAddress;
import uk.nhs.ciao.transport.spine.sds.EndpointAddressRepository;

/**
 * Creates a service route which resolves {@link EndpointAddress} using
 * the specified {@link EndpointAddressRepository}.
 */
public class EndpointAddressServiceRoute extends BaseRouteBuilder {
	public static final String INTERACTION = "interaction";
	public static final String ADDRESS = "address";
	
	private String endpointAddressServiceUrl;
	private EndpointAddressRepository endpointAddressRepository;
	
	public void setEndpointAddressServiceUri(final String endpointAddressServiceUrl) {
		this.endpointAddressServiceUrl = endpointAddressServiceUrl;
	}
	
	public void setEndpointAddressRepository(final EndpointAddressRepository endpointAddressRepository) {
		this.endpointAddressRepository = endpointAddressRepository;
	}
	
	@Override
	public void configure() throws Exception {
		Preconditions.checkNotNull(endpointAddressRepository, "endpointAddressServiceUrl is required");
		
		from(endpointAddressServiceUrl)
			.setBody().constant(null)
			.choice()
				.when().simple("${headers.address.isASID}")
					.bean(endpointAddressRepository, "findByAsid(${headers.interaction}, ${headers.address.uri})")
				.endChoice()
				.when().simple("${headers.address.isODS}")
					.bean(endpointAddressRepository, "findByODSCode(${headers.interaction}, ${headers.address.getODSCode})")
				.endChoice()
			.end()
		.end();
	}
}
