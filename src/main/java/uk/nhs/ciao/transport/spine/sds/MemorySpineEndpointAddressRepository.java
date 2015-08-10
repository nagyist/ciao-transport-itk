package uk.nhs.ciao.transport.spine.sds;

import java.util.Collection;
import java.util.Map;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

/**
 * A {@link SpineEndpointAddressRepository} which stores addresses in memory
 */
public class MemorySpineEndpointAddressRepository implements SpineEndpointAddressRepository {
	private final Map<ODSKey, SpineEndpointAddress> odsIndex = Maps.newConcurrentMap();
	private final Map<ASIDKey, SpineEndpointAddress> asidIndex = Maps.newConcurrentMap();
	
	@Override
	public SpineEndpointAddress findByODSCode(final String service, final String action, final String odsCode) {
		return createCopy(odsIndex.get(new ODSKey(service, action, odsCode)));
	}
	
	@Override
	public SpineEndpointAddress findByAsid(final String service, final String action, final String asid) {
		return createCopy(asidIndex.get(new ASIDKey(service, action, asid)));
	}
	
	public void storeAll(final Collection<? extends SpineEndpointAddress> addresses) {
		if (addresses == null) {
			return;
		}
		
		for (final SpineEndpointAddress address: addresses) {
			if (address == null) {
				continue;
			}
			
			// Store a defensive copy
			final SpineEndpointAddress addressToStore = createCopy(address);
			odsIndex.put(new ODSKey(addressToStore), addressToStore);
			asidIndex.put(new ASIDKey(addressToStore), addressToStore);
		}
	}
	
	/**
	 * Null-safe copy
	 * <p>
	 * Addresses are mutable - so use defensive copies to ensure in-memory instances are
	 * not accidently changed by client classes
	 */
	private SpineEndpointAddress createCopy(final SpineEndpointAddress address) {
		return address == null ? null : new SpineEndpointAddress(address);
	}
	
	private static class ODSKey {
		private final String service;
		private final String action;
		private final String odsCode;
		
		public ODSKey(final SpineEndpointAddress address) {
			this(address.getService(), address.getAction(), address.getOdsCode());
		}
		
		public ODSKey(final String service, final String action, final String odsCode) {
			this.service = service;
			this.action = action;
			this.odsCode = odsCode;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((service == null) ? 0 : service.hashCode());
			result = prime * result
					+ ((action == null) ? 0 : action.hashCode());
			result = prime * result
					+ ((odsCode == null) ? 0 : odsCode.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			} else if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			
			final ODSKey other = (ODSKey) obj;
			return Objects.equal(service, other.service)
					&& Objects.equal(action, other.action)
					&& Objects.equal(odsCode, other.odsCode);
		}
	}
	
	private static class ASIDKey {
		private final String service;
		private final String action;
		private final String asid;
		
		public ASIDKey(final SpineEndpointAddress address) {
			this(address.getService(), address.getAction(), address.getAsid());
		}
		
		public ASIDKey(final String service, final String action, final String asid) {
			this.service = service;
			this.action = action;
			this.asid = asid;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((service == null) ? 0 : service.hashCode());
			result = prime * result
					+ ((action == null) ? 0 : action.hashCode());
			result = prime * result
					+ ((asid == null) ? 0 : asid.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			} else if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			
			final ASIDKey other = (ASIDKey) obj;
			return Objects.equal(service, other.service)
					&& Objects.equal(action, other.action)
					&& Objects.equal(asid, other.asid);
		}
	}
}
