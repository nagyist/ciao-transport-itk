package uk.nhs.ciao.transport.dts.address;

import com.google.common.base.Objects;

import uk.nhs.ciao.logging.CiaoLogMessage;

public class DTSEndpointAddressIdentifier {
	public static DTSEndpointAddressIdentifier byODSCode(final String workflowId, final String odsCode) {
		return new DTSEndpointAddressIdentifier(workflowId, odsCode);
	}

	private final String workflowId;
	private final String odsCode;
	
	public DTSEndpointAddressIdentifier(final String workflowId, final String odsCode) {
		this.workflowId = workflowId;
		this.odsCode = odsCode;
	}
	
	public String getWorkflowId() {
		return workflowId;
	}
	
	public String getOdsCode() {
		return odsCode;
	}

	public void addToLog(final CiaoLogMessage logMsg) {
		logMsg.workflowId(workflowId);
		logMsg.odsCode(odsCode);
	}

	public String getKey() {
		return workflowId + "/ODS/" + odsCode;
	}
	
	@Override
	public String toString() {
		return getKey();
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((workflowId == null) ? 0 : workflowId.hashCode());
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
		
		final DTSEndpointAddressIdentifier other = (DTSEndpointAddressIdentifier) obj;
		return Objects.equal(workflowId, other.workflowId)
				&& Objects.equal(odsCode, other.odsCode);
	}
}
