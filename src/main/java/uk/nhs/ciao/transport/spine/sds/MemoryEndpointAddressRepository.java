package uk.nhs.ciao.transport.spine.sds;

import java.util.Collection;
import java.util.Map;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

/**
 * A {@link EndpointAddressRepository} which stores addresses in memory
 */
public class MemoryEndpointAddressRepository implements EndpointAddressRepository {
	private final Map<ODSKey, EndpointAddress> odsIndex = Maps.newConcurrentMap();
	private final Map<ASIDKey, EndpointAddress> asidIndex = Maps.newConcurrentMap();
	
	@Override
	public EndpointAddress findByODSCode(final String interaction, final String odsCode) {
		return odsIndex.get(new ODSKey(interaction, odsCode));
	}
	
	@Override
	public EndpointAddress findByAsid(final String interaction, final String asid) {
		return asidIndex.get(new ASIDKey(interaction, asid));
	}
	
	public void storeAll(final Collection<? extends EndpointAddress> collection) {
		if (collection == null) {
			return;
		}
		
		for (final EndpointAddress address: collection) {
			if (address == null) {
				continue;
			}
			
			odsIndex.put(new ODSKey(address.getInteraction(), address.getOdsCode()), address);
			asidIndex.put(new ASIDKey(address.getInteraction(), address.getAsid()), address);
		}
	}
	
	private static class ODSKey {
		private final String interaction;
		private final String odsCode;
		
		public ODSKey(final String interaction, final String odsCode) {
			this.interaction = interaction;
			this.odsCode = odsCode;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((interaction == null) ? 0 : interaction.hashCode());
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
			return Objects.equal(interaction, other.interaction)
					&& Objects.equal(odsCode, other.odsCode);
		}
	}
	
	private static class ASIDKey {
		private final String interaction;
		private final String asid;
		
		public ASIDKey(final String interaction, final String asid) {
			this.interaction = interaction;
			this.asid = asid;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((interaction == null) ? 0 : interaction.hashCode());
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
			return Objects.equal(interaction, other.interaction)
					&& Objects.equal(asid, other.asid);
		}
	}
}
