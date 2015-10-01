package uk.nhs.ciao.transport.itk.address;

import java.util.Collection;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

/**
 * A {@link EndpointAddressRepository} which stores addresses in memory
 * <p>
 * Concrete address type details and searching for associated address identifiers is
 * handled by the configured {@link EndpointAddressHelper}.
 * 
 * @param <ID> The type of address id
 * @param <A> The type of addresses handled by this class
 */
public class MemoryEndpointAddressRepository<ID, A> implements EndpointAddressRepository<ID, A> {
	private final EndpointAddressHelper<ID, A> helper;
	private final Map<ID, A> index;
	
	public MemoryEndpointAddressRepository(final EndpointAddressHelper<ID, A> helper) {
		this.helper = Preconditions.checkNotNull(helper);
		this.index = Maps.newConcurrentMap();
	}
	
	@Override
	public A findAddress(final ID identifier) {
		return identifier == null ? null : helper.copyAddress(index.get(identifier));
	}
	
	/**
	 * Adds the specified addresses to the backing index
	 * <p>
	 * The configured {@link EndpointAddressHelper} is queried to determine available
	 * identifiers for each address.
	 * 
	 * @param addresses The addresses to add
	 */
	public void storeAll(final Collection<? extends A> addresses) {
		if (addresses == null) {
			return;
		}
		
		for (final A address: addresses) {
			if (address == null) {
				continue;
			}
			
			// Store a defensive copy
			final A addressToStore = helper.copyAddress(address);
			for (final ID id: helper.findIdentifiers(addressToStore)) {
				if (id != null) {
					index.put(id, address);
				}
			}
		}
	}
}
