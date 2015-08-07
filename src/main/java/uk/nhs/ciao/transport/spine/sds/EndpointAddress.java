package uk.nhs.ciao.transport.spine.sds;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

/**
 * Details to identify / address an Accredited System end-point over spine
 */
public class EndpointAddress {
	/**
	 * Identifies the organisation associated with the Accredited System
	 */
	private String odsCode;
	private String interaction;
	
	/**
	 * Identifies the Accredited System
	 */
	private String asid;
	
	/**
	 * Identifies ContractProperties for Party + Interaction
	 */
	private String cpaId;
	
	/**
	 * Identifies the message handling service (MHS) responsible for sending
	 * messages to the Accredited System
	 */
	private String mhsPartyKey;
	
	
	
	public EndpointAddress() {
		// NOOP
	}
	
	/**
	 * Copy constructor
	 */
	public EndpointAddress(final EndpointAddress copy) {
		odsCode = copy.odsCode;
		interaction = copy.interaction;
		asid = copy.asid;
		cpaId = copy.cpaId;
		mhsPartyKey = copy.mhsPartyKey;
	}
	
	public String getOdsCode() {
		return odsCode;
	}
	
	public void setOdsCode(final String odsCode) {
		this.odsCode = odsCode;
	}
	
	public String getInteraction() {
		return interaction;
	}
	
	public void setInteraction(final String interaction) {
		this.interaction = interaction;
	}
	
	public String getAsid() {
		return asid;
	}
	
	public void setAsid(final String asid) {
		this.asid = asid;
	}
	
	public String getCpaId() {
		return cpaId;
	}
	
	public void setCpaId(final String cpaId) {
		this.cpaId = cpaId;
	}
	
	public String getMhsPartyKey() {
		return mhsPartyKey;
	}
	
	public void setMhsPartyKey(final String mhsPartyKey) {
		this.mhsPartyKey = mhsPartyKey;
	}
	
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
			.add("odsCode", odsCode)
			.add("interaction", interaction)
			.add("asid", asid)
			.add("cpaId", cpaId)
			.add("mhsPartyKey", mhsPartyKey)
			.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((asid == null) ? 0 : asid.hashCode());
		result = prime * result + ((cpaId == null) ? 0 : cpaId.hashCode());
		result = prime * result + ((interaction == null) ? 0 : interaction.hashCode());
		result = prime * result + ((mhsPartyKey == null) ? 0 : mhsPartyKey.hashCode());
		result = prime * result + ((odsCode == null) ? 0 : odsCode.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		} else if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		
		final EndpointAddress other = (EndpointAddress) obj;
		return Objects.equal(asid, other.asid)
				&& Objects.equal(cpaId, other.cpaId)
				&& Objects.equal(interaction, other.interaction)
				&& Objects.equal(mhsPartyKey, other.mhsPartyKey)
				&& Objects.equal(odsCode, other.odsCode);
	}	
}
