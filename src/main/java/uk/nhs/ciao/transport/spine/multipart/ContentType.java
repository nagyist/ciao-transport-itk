package uk.nhs.ciao.transport.spine.multipart;

import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;

/**
 * Represents a structure view of a Content-Type header value
 * 
 * @see http://www.w3.org/Protocols/rfc1341/4_Content-Type.html
 */
public class ContentType {
	public static final String BOUNDARY = "boundary";
	public static final String START = "start";
	
	private static final Pattern MAIN_AND_SUB_TYPE_PATTERN = Pattern.compile("\\A([\\w\\*]*)/([\\w\\*]*)");
	private static final Pattern PARAMETER_PATTERN = Pattern.compile(";\\s*([\\w]+)=\"?([^\";]+)\"?");
	
	private final String mainType;
	private final String subType;
	private final EntrySeries parameters;
	
	public ContentType(final String mainType, final String subType) {
		this.mainType = Preconditions.checkNotNull(mainType);
		this.subType = Preconditions.checkNotNull(subType);
		this.parameters = new EntrySeries();
	}
	
	public ContentType(final ContentType copy) {
		this.mainType = copy.mainType;
		this.subType = copy.subType;
		this.parameters = new EntrySeries(copy.parameters);
	}
	
	public EntrySeries getParameters() {
		return parameters;
	}
	
	public String getBoundary() {
		return parameters.getFirstValue(BOUNDARY);
	}
	
	public void setBoundary(final String boundary) {
		parameters.setOrRemove(BOUNDARY, boundary);
	}
	
	public String getStart() {
		return parameters.getFirstValue(START);
	}
	
	public void setStart(final String start) {
		parameters.setOrRemove(START, start);
	}
	
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		toString(builder);
		return builder.toString();
	}
	
	public void toString(final StringBuilder builder) {
		final boolean quoteParameters = true;
		toString(builder, quoteParameters);
	}
	
	public void toString(final StringBuilder builder, final boolean quoteParameters) {
		builder.append(mainType)
			.append("/")
			.append(subType);
		
		for (final Entry<String, String> parameter: parameters) {
			builder.append("; ")
				.append(parameter.getKey())
				.append("=");
			
			if (quoteParameters) {
				builder.append("\"")
					.append(parameter.getValue())
					.append("\"");
			} else {
				builder.append(parameter.getValue());
			}
		}
	}
	
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
