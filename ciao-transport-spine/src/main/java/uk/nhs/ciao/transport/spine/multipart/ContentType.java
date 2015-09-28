package uk.nhs.ciao.transport.spine.multipart;

import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;

import com.google.common.base.Preconditions;

/**
 * Represents a structure view of a Content-Type header value
 * 
 * @see http://www.w3.org/Protocols/rfc1341/4_Content-Type.html
 */
public class ContentType {
	/**
	 * The Content-Type header name 
	 */
	public static final String HEADER_NAME = Exchange.CONTENT_TYPE;
	
	/**
	 * The boundary parameter name
	 */
	public static final String BOUNDARY = "boundary";
	
	/**
	 * The start parameter name
	 */
	public static final String START = "start";
	
	private static final Pattern MAIN_AND_SUB_TYPE_PATTERN = Pattern.compile("\\A([\\w\\*]*)/([\\w\\*]*)");
	private static final Pattern PARAMETER_PATTERN = Pattern.compile(";\\s*([\\w]+)=\"?([^\";]+)\"?");
	
	private final String mainType;
	private final String subType;
	private final EntrySeries parameters;
	
	/**
	 * Creates a new ContentType instance for the specified main and sub types.
	 * <p>
	 * The created instance contains no parameters
	 */
	public ContentType(final String mainType, final String subType) {
		this.mainType = Preconditions.checkNotNull(mainType);
		this.subType = Preconditions.checkNotNull(subType);
		this.parameters = new EntrySeries();
	}
	
	/**
	 * ContentType copy constructor
	 */
	public ContentType(final ContentType copy) {
		this.mainType = copy.mainType;
		this.subType = copy.subType;
		this.parameters = new EntrySeries(copy.parameters);
	}
	
	/**
	 * The required main type
	 * <p>
	 * e.g. text or application etc...
	 */
	public String getMainType() {
		return mainType;
	}
	
	/**
	 * The required sub type
	 * <p>
	 * e.g. xml or plain etc...
	 */
	public String getSubType() {
		return subType;
	}
	
	/**
	 * The series of optional name/value parameters
	 * <p>
	 * Any changes made on the series instance are reflected in this ContentType.
	 */
	public EntrySeries getParameters() {
		return parameters;
	}
	
	/**
	 * The (optional) boundary parameter
	 * 
	 * @see #BOUNDARY
	 */
	public String getBoundary() {
		return parameters.getFirstValue(BOUNDARY);
	}
	
	/**
	 * The (optional) boundary parameter
	 * 
	 * @see #BOUNDARY
	 */
	public void setBoundary(final String boundary) {
		parameters.setOrRemove(BOUNDARY, boundary);
	}
	
	/**
	 * The (optional) start parameter
	 * 
	 * @see #START
	 */
	public String getStart() {
		return parameters.getFirstValue(START);
	}
	
	/**
	 * The (optional) start parameter
	 * 
	 * @see #START
	 */
	public void setStart(final String start) {
		parameters.setOrRemove(START, start);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		toString(builder);
		return builder.toString();
	}
	
	/**
	 * Encodes the content type in string form by appending it to the builder.
	 * <p>
	 * Any parameters are always quoted
	 */
	public void toString(final StringBuilder builder) {
		final boolean alwaysQuoteParameters = true;
		toString(builder, alwaysQuoteParameters);
	}
	
	/**
	 * Encodes the content type in string form by appending it to the builder.
	 * 
	 * @param alwaysQuoteParameters true if parameters should always be quoted
	 */
	public void toString(final StringBuilder builder, final boolean alwaysQuoteParameters) {
		builder.append(mainType)
			.append("/")
			.append(subType);
		
		for (final Entry<String, String> parameter: parameters) {
			builder.append("; ")
				.append(parameter.getKey())
				.append("=");
			
			if (alwaysQuoteParameters) {
				builder.append("\"")
					.append(parameter.getValue())
					.append("\"");
			} else {
				builder.append(parameter.getValue());
			}
		}
	}
	
	/**
	 * Returns an instance representing the specified string value
	 */
	public static ContentType valueOf(final String value) {
		if (value == null) {
			return null;
		}
		
		Matcher matcher = MAIN_AND_SUB_TYPE_PATTERN.matcher(value);
		if (!matcher.find()) {
			return null;
		}
		
		final String mainType = matcher.group(1);
		final String subType = matcher.group(2);
		final ContentType contentType = new ContentType(mainType, subType);
		
		matcher = PARAMETER_PATTERN.matcher(value);
		while (matcher.find()) {
			contentType.getParameters().add(matcher.group(1), matcher.group(2));
		}
		
		return contentType;
	}
}
