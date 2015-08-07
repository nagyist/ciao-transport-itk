package uk.nhs.ciao.transport.spine.sds;

import java.util.Collection;
import java.util.Map;

import com.google.common.collect.Maps;

/**
 * A {@link EndpointAddressRepository} which stores addresses in memory
 */
public class MemoryEndpointAddressRepository implements EndpointAddressRepository {
	private final Map<String, EndpointAddress> index = Maps.newConcurrentMap();
	
	@Override
	public EndpointAddress findByODSCode(final String interaction, final String odsCode) {
		return index.get(odsCode);
	}
	
	@Override
	public EndpointAddress findByAsid(final String interaction, final String asid) {
		return index.get(asid);
	}
	
	public void storeAll(final Collection<? extends EndpointAddress> collection) {
		if (collection == null) {
			return;
		}
		
		for (final EndpointAddress address: collection) {
			if (address == null || address.getOdsCode() == null) {
				continue;
			}
			
			index.put(address.getOdsCode(), address);
		}
	}
}
