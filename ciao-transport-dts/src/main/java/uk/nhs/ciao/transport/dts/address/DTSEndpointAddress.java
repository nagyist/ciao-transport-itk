package uk.nhs.ciao.transport.dts.address;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

/**
 * Details to identify a destination end-point over DTS
 */
public class DTSEndpointAddress {
	private String workflowId;
	private String odsCode;
	
	// TODO: Add additional properties -> DTS mailbox etc
	
	public DTSEndpointAddress() {
		// NOOP
	}

	/**
	 * Copy constructor
	 */
	public DTSEndpointAddress(final DTSEndpointAddress copy) {
		workflowId = copy.workflowId;
		odsCode = copy.odsCode;
	}

	public String getWorkflowId() {
		return workflowId;
	}

	public void setWorkflowId(final String workflowId) {
		this.workflowId = workflowId;
	}
	
	public String getOdsCode() {
		return odsCode;
	}
	
	public void setOdsCode(final String odsCode) {
		this.odsCode = odsCode;
	}
	
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
			.add("workflowId", workflowId)
			.add("odsCode", odsCode)
			.toString();
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((workflowId == null) ? 0 : workflowId.hashCode());
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
		
		final DTSEndpointAddress other = (DTSEndpointAddress) obj;
		return Objects.equal(workflowId, other.workflowId)
				&& Objects.equal(odsCode, other.odsCode);
	}	
}
