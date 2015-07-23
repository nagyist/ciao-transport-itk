package uk.nhs.ciao.transport.spine.multipart;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.impl.DefaultMessage;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

/**
 * Represents a part/entity contained within a multipart body
 * <p>
 * The entity is made up of a series of name/value headers and a body.
 */
public class Part extends DefaultMessage {
	public static final String CONTENT_ID = "Content-Id";
	public static final String CONTENT_TYPE = "Content-Type";
	public static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";

	private static final String CRLF = "\r\n";
	private static final String HEADER_SEPARATOR = ": ";
	private static final Pattern RAW_CONTENT_ID_PATTERN = Pattern.compile("\\A\\s*<(.*)>\\s*\\Z");
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * Represents the name/value headers of a part.
	 * <p>
	 * Multiple values for the same name are allowed and names are case-insensitive (RFC 5234, 2045, 822).
	 */
	@Override
	public Map<String, Object> getHeaders() {
		return super.getHeaders();
	}
	
	public String getRawContentId() {
		return getFirstHeader(CONTENT_ID);
	}
	
	public void setRawContentId(final String rawContentId) {
		setOrRemoveHeader(CONTENT_ID, rawContentId);
	}

	public String getContentId() {
		String rawContentId = getRawContentId();
		if (rawContentId == null) {
			return null;
		}
		
		final Matcher matcher = RAW_CONTENT_ID_PATTERN.matcher(rawContentId);
		return matcher.matches() ? matcher.group(1) : rawContentId;
	}
	
	public void setContentId(final String contentId) {
		final String rawContentId;
		
		if (contentId == null) {
			rawContentId = null;
		} else if (RAW_CONTENT_ID_PATTERN.matcher(contentId).matches()) {
			rawContentId = contentId;
		} else {
			rawContentId = "<" + contentId + ">";
		}
		
		setRawContentId(rawContentId);
	}
	
	public String getContentType() {
		return getFirstHeader(CONTENT_TYPE);
	}
	
	public void setContentType(final String contentType) {
		setOrRemoveHeader(CONTENT_TYPE, contentType);
	}
	
	public String getContentTransferEncoding() {
		return getFirstHeader(CONTENT_TRANSFER_ENCODING);
	}
	
	public void setContentTransferEncoding(final String contentTransferEncoding) {
		setOrRemoveHeader(CONTENT_TRANSFER_ENCODING, contentTransferEncoding);
	}
	
	public String getFirstHeader(final String name) {
		final String defaultValue = null;
		return getFirstHeader(name, defaultValue);
	}
	
	public String getFirstHeader(final String name, final String defaultValue) {
		Object value = getHeader(name);
		if (value instanceof List<?>) {
			final List<?> values = (List<?>)value;
			value = values.isEmpty() ? null : values.get(0);
		}
		
		return value == null ? defaultValue : value.toString();
	}
	
	public void addHeader(final String name, final String value) {
		Preconditions.checkNotNull(name, "name");
		Preconditions.checkNotNull(value, "value");
		
		Object previousValue = getHeader(name);
		if (previousValue == null) {
			// first value - store string directly
			setHeader(name, value);
		} else if (previousValue instanceof List<?>) {
			// multiple existing values - add to end of list
			@SuppressWarnings("unchecked")
			final List<? super String> values = (List<? super String>)previousValue;
			values.add(value);
		} else {
			// second value - move values into list form
			final List<Object> values = Lists.newArrayList();
			values.add(previousValue);
			values.add(value);
			setHeader(name, values);
		}
	}
	
	public void setOrRemoveHeader(final String name, final String value) {
		if (value == null) {
			removeHeader(name);
		} else {
			getHeaders().put(name, value);
		}
	}
	
	public void write(final OutputStream out) throws IOException {
		writeHeaders(out);
		writeBody(out);
	}
	
	private void writeHeaders(final OutputStream out) throws IOException {
		for (final Entry<String, Object> entry: getHeaders().entrySet()) {
			writeHeader(out, entry.getKey(), entry.getValue());
		}
		out.write(CRLF.getBytes());
	}
	
	private void writeHeader(final OutputStream out, final String name, final Object value) throws IOException {
		if (value instanceof List<?>) {
			for (final Object nestedValue: (List<?>)value) {
				writeHeader(out, name, nestedValue);
			}
		} else if (value != null) {
			out.write(name.getBytes());
			out.write(HEADER_SEPARATOR.getBytes());
			out.write(value.toString().getBytes());
			out.write(CRLF.getBytes());
		}
	}
	
	private void writeBody(final OutputStream out) throws IOException {
		if (getBody() == null) {
			// Nothing to do
			return;
		}
		
		// Try to write the body from a couple of standard types
		// If the exchange has been set, camel's type converter should
		// be able to convert the body
		// try a stream first, falling back to a string is that fails
		if (!writeBodyAsStream(out) && !writeBodyAsString(out)) {
			throw new IOException("Unable to write body of Part: " + getBody());
		}
	}
	
	private boolean writeBodyAsStream(final OutputStream out) throws IOException {
		InputStream body = getBody(InputStream.class);
		if (body == null) {
			return false;
		}
		
		try {
			ByteStreams.copy(body, out);
		} finally {
			Closeables.closeQuietly(body);
		}
		
		return true;
	}
	
	private boolean writeBodyAsString(final OutputStream out) throws IOException {
		String body = getBody(String.class);
		if (body == null) {
			return false;
		}
		
		out.write(body.getBytes());
		return true;
	}
}
