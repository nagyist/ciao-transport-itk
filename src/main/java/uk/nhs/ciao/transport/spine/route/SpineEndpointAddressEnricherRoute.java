package uk.nhs.ciao.transport.spine.route;

import com.google.common.base.Preconditions;

import uk.nhs.ciao.transport.spine.sds.SpineEndpointAddress;
import uk.nhs.ciao.transport.spine.sds.SpineEndpointAddressRepository;

/**
 * Creates a service route which enriches {@link SpineEndpointAddress} using
 * the specified {@link SpineEndpointAddressRepository}.
 */
public class SpineEndpointAddressEnricherRoute extends BaseRouteBuilder {
	private String spineEndpointAddressEnricherUrl;
	private SpineEndpointAddressRepository spineEndpointAddressRepository;
	
	public void setSpineEndpointAddressEnricherUri(final String spineEndpointAddressEnricherUrl) {
		this.spineEndpointAddressEnricherUrl = spineEndpointAddressEnricherUrl;
	}
	
	public void setSpineEndpointAddressRepository(final SpineEndpointAddressRepository spineEndpointAddressRepository) {
		this.spineEndpointAddressRepository = spineEndpointAddressRepository;
	}
	
	@Override
	public void configure() throws Exception {
		Preconditions.checkNotNull(spineEndpointAddressRepository, "spineEndpointAddressRepository is required");
		
		from(spineEndpointAddressEnricherUrl)
			.setProperty("spineEndpointAddress").body(SpineEndpointAddress.class)
			.choice()
				.when().simple("${body.asid}")
					.bean(spineEndpointAddressRepository, "findByAsid(${body.service}, ${body.action}, ${body.asid})")
				.endChoice()
				.when().simple("${body.odsCode}")
					.bean(spineEndpointAddressRepository, "findByODSCode(${body.service}, ${body.action}, ${body.odsCode})")
				.endChoice()
			.end()
			
			.choice()
				.when().simple("${body} == null")
					.setBody().property("spineEndpointAddress")
				.endChoice()
			.end()
			
			.removeProperty("spineEndpointAddress")
			
		.end();
	}
}
