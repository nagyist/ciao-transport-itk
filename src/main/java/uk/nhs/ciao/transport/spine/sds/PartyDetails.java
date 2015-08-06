package uk.nhs.ciao.transport.spine.sds;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

/**
 * Details to identify / address a party
 */
public class PartyDetails {
	private String odsCode;
	private String asid;
	private String partyKey;
	private String cpaId;
	
	public String getOdsCode() {
		return odsCode;
	}
	
	public void setOdsCode(final String odsCode) {
		this.odsCode = odsCode;
	}
	
	public String getAsid() {
		return asid;
	}
	
	public void setAsid(final String asid) {
		this.asid = asid;
	}
	
	public String getPartyKey() {
		return partyKey;
	}
	
	public void setPartyKey(final String partyKey) {
		this.partyKey = partyKey;
	}
	
	public String getCpaId() {
		return cpaId;
	}
	
	public void setCpaId(final String cpaId) {
		this.cpaId = cpaId;
	}
	
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
			.add("odsCode", odsCode)
			.add("asid", asid)
			.add("partyKey", partyKey)
			.add("cpaId", cpaId)
			.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((asid == null) ? 0 : asid.hashCode());
		result = prime * result + ((cpaId == null) ? 0 : cpaId.hashCode());
		result = prime * result + ((partyKey == null) ? 0 : partyKey.hashCode());
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
		
		final PartyDetails other = (PartyDetails) obj;
		return Objects.equal(asid, other.asid)
				&& Objects.equal(cpaId, other.cpaId)
				&& Objects.equal(partyKey, other.partyKey)
				&& Objects.equal(odsCode, other.odsCode);
	}	
}
