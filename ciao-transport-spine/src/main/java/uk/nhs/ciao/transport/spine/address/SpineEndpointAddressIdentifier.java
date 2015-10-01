package uk.nhs.ciao.transport.spine.address;

import com.google.common.base.Objects;

import uk.nhs.ciao.logging.CiaoLogMessage;

public class SpineEndpointAddressIdentifier {
	public enum CodeType {
		ASID, ODS;
	}
	
	private final String service;
	private final String action;
	private final CodeType codeType;
	private final String code;
	
	public static SpineEndpointAddressIdentifier byAsid(final String service, final String action, final String asid) {
		return new SpineEndpointAddressIdentifier(service, action, CodeType.ASID, asid);
	}
	
	public static SpineEndpointAddressIdentifier byODSCode(final String service, final String action, final String odsCode) {
		return new SpineEndpointAddressIdentifier(service, action, CodeType.ODS, odsCode);
	}
	
	public SpineEndpointAddressIdentifier(final String service, final String action, final CodeType codeType, final String code) {
		this.service = service;
		this.action = action;
		this.codeType = codeType;
		this.code = code;
	}
	
	public String getService() {
		return service;
	}
	
	public String getAction() {
		return action;
	}
	
	public CodeType getCodeType() {
		return codeType;
	}
	
	public String getCode() {
		return code;
	}
	
	public String getAsid() {
		return codeType == CodeType.ASID ? code : null;
	}
	
	public String getODSCode() {
		return codeType == CodeType.ODS ? code : null;
	}

	public void addToLog(final CiaoLogMessage logMsg) {
		logMsg.service(service).action(action);
		
		if (codeType == CodeType.ODS) {
			logMsg.odsCode(code);
		} else if (codeType == CodeType.ASID) {
			logMsg.asid(code);
		}		
	}

	public String getKey() {
		return service + ':' + action + '/' + codeType + '/' + code;
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
				+ ((service == null) ? 0 : service.hashCode());
		result = prime * result
				+ ((action == null) ? 0 : action.hashCode());
		result = prime * result
				+ ((codeType == null) ? 0 : codeType.hashCode());
		result = prime * result
				+ ((code == null) ? 0 : code.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		} else if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		
		final SpineEndpointAddressIdentifier other = (SpineEndpointAddressIdentifier) obj;
		return Objects.equal(service, other.service)
				&& Objects.equal(action, other.action)
				&& Objects.equal(codeType, other.codeType)
				&& Objects.equal(code, code);
	}
}
