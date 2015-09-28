package uk.nhs.ciao.transport.spine.multipart;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;

/**
 * Represents a structure view of a Content-Id header value
 */
public class ContentId {
	public static final String HEADER_NAME = "Content-Id";
	private static final Pattern RAW_VALUE_PATTERN = Pattern.compile("\\A\\s*<(.*)>\\s*\\Z");

	private final String rawValue;
	private volatile String value; // lazy-loaded
	
	/**
	 * Constructs a new instance of ContentId using the specified raw string value
	 */
	public ContentId(final String rawValue) {
		this.rawValue = encodeRawValue(Preconditions.checkNotNull(rawValue));
	}
	
	/**
	 * The raw/encoded value of the ContentId
	 * <p>
	 * e.g. <code>&lt;1234-5678&gt;</code>
	 */
	public String getRawValue() {
		return rawValue;
	}
	
	/**
	 * The interpreted/inner value of the ContentId
	 * e.g. <code>1234-5678</code>
	 */
	public String getValue() {
		String result = value;
		if (result == null) {
			result = decodeRawValue(rawValue);
			value = result;
		}
		
		return result;
	}
	
	/**
	 * Returns the raw form of the ContentId
	 */
	@Override
	public String toString() {
		return rawValue;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return rawValue.hashCode();
	}
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * ContentIds are considered equal if they have the same raw value.
	 */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		} else if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		
		final ContentId other = (ContentId)obj;
		return rawValue.equals(other.rawValue);
	}
	
	/**
	 * Returns an instance of ContentId representing the specified string value
	 */
	public static ContentId valueOf(final String rawValue) {
		return new ContentId(rawValue);
	}
	
	/**
	 * Encodes the specified string value in raw form.
	 * <p>
	 * e.g. <code>&lt;1234-5678&gt;</code>
	 */
	public static String encodeRawValue(final String value) {
		final String rawContentId;
		
		if (value == null) {
			rawContentId = null;
		} else if (RAW_VALUE_PATTERN.matcher(value).matches()) {
			rawContentId = value;
		} else {
			rawContentId = "<" + value + ">";
		}
		
		return rawContentId;
	}
	
	/**
	 * Decodes the specified string value from raw form to interpret the inner value.
	 * <p>
	 * e.g. <code>1234-5678</code>
	 */
	public static String decodeRawValue(final String rawValue) {
		if (rawValue == null) {
			return null;
		}
		
		final Matcher matcher = RAW_VALUE_PATTERN.matcher(rawValue);
		return matcher.matches() ? matcher.group(1) : rawValue;
	}
}
