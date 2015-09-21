package uk.nhs.ciao.transport.spine.itk;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;

public class Address {
	/**
	 * URI is the spine ASID - no additional parsing is required on the URI
	 */
	public static final String ASID_TYPE = "1.2.826.0.1285.0.2.0.107";
	
	/**
	 * URI starts with {@link #ODS_URI_PREFIX} - the next colon delimited segment
	 * is the ODS code. The URI may contain additional segments which do not
	 * form part of the ODS code.
	 */
	public static final String ITK_ADDRESS_TYPE = "2.16.840.1.113883.2.1.3.2.4.18.22";
	public static final String DEFAULT_TYPE = ITK_ADDRESS_TYPE;
	
	private static final String ODS_URI_PREFIX = "urn:nhs-uk:addressing:ods:";
	private String type;
	private String uri;
	
	public Address() {
		// NOOP
	}
	
	public Address(final String type, final String uri) {
		this.type = type;
		this.uri = uri;
	}
	
	public Address(final String uri) {
		this.uri = uri;
	}
	
	/**
	 * Copy constructor
	 */
	public Address(final Address copy) {
		this.type = copy.type;
		this.uri = copy.uri;
	}
	
	public String getType() {
		return type;
	}
	
	public void setType(final String type) {
		this.type = type;
	}
	
	public String getUri() {
		return uri;
	}
	
	public void setUri(final String uri) {
		this.uri = uri;
	}
	
	public boolean isDefaultType() {
		return Strings.isNullOrEmpty(type) || DEFAULT_TYPE.equals(type);
	}
	
	public boolean isODS() {
		return isDefaultType();
	}
	
	public boolean isASID() {
		return ASID_TYPE.equals(type);
	}
	
	public void setODSCode(final String odsCode) {
		this.uri = odsCode == null ? null : ODS_URI_PREFIX + odsCode;
	}
	
	public String getODSCode() {
		if (uri == null || !uri.startsWith(ODS_URI_PREFIX)) {
			return null;
		}
		
		final String odsCode = uri.substring(ODS_URI_PREFIX.length());
		
		// Strip any trailing segments
		final int index = odsCode.indexOf(':');
		return index < 0 ? odsCode : odsCode.substring(0, index);
	}
	
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
			.add("type", type)
			.add("uri", uri)
			.toString();
	}
}