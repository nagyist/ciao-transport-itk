package uk.nhs.ciao.transport.spine.itk;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;

public class Identity {
	public static final String DEFAULT_TYPE = "2.16.840.1.113883.2.1.3.2.4.18.27";
	
	private static final String ODS_URI_PREFIX = "urn:nhs-uk:identity:";
	
	private String type;
	private String uri;
	
	public Identity() {
		// NOOP
	}
	
	public Identity(final String type, final String uri) {
		this.type = type;
		this.uri = uri;
	}
	
	public Identity(final String uri) {
		this.uri = uri;
	}
	
	/**
	 * Copy constructor
	 */
	public Identity(final Identity copy) {
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
	
	public void setODSCode(final String odsCode) {
		this.uri = odsCode == null ? null : ODS_URI_PREFIX + odsCode;
	}
	
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
			.add("type", type)
			.add("uri", uri)
			.toString();
	}
}