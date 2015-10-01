package uk.nhs.ciao.transport.itk.route;

import com.google.common.base.Preconditions;

import uk.nhs.ciao.camel.BaseRouteBuilder;
import uk.nhs.ciao.transport.itk.address.EndpointAddressHelper;
import uk.nhs.ciao.transport.itk.address.EndpointAddressRepository;

/**
 * Creates a service route which enriches an endpoint address using
 * the specified {@link EndpointAddressRepository}.
 */
public class EndpointAddressEnricherRoute<ID, A> extends BaseRouteBuilder {
	private String spineEndpointAddressEnricherUrl;
	private EndpointAddressRepository<ID, A> endpointAddressRepository;
	private EndpointAddressHelper<ID, A> helper;
	
	public void setSpineEndpointAddressEnricherUri(final String spineEndpointAddressEnricherUrl) {
		this.spineEndpointAddressEnricherUrl = spineEndpointAddressEnricherUrl;
	}
	
	public void setEndpointAddressRepository(final EndpointAddressRepository<ID, A> endpointAddressRepository) {
		this.endpointAddressRepository = endpointAddressRepository;
	}
	
	public void setHelper(final EndpointAddressHelper<ID, A> helper) {
		this.helper = helper;
	}
	
	@Override
	public void configure() throws Exception {
		Preconditions.checkNotNull(endpointAddressRepository, "spineEndpointAddressRepository is required");
		Preconditions.checkNotNull(helper, "helper is required");
		
		from(spineEndpointAddressEnricherUrl)
			.convertBodyTo(helper.getAddressType())
			.bean(new EndpointAddressEnricher())
		.end();
	}
	
	// processor methods are kept out of the main class to avoid problems
	// with the camel tracer / logging
	
	public class EndpointAddressEnricher {
		public A enrich(final A address) throws Exception {
			final ID identifier = helper.findBestIdentifier(address);
			final A enrichedAddress = endpointAddressRepository.findAddress(identifier);
			return enrichedAddress == null ? address : enrichedAddress;
		}
	}
}
