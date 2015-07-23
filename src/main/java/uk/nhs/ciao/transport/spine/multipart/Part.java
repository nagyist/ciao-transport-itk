package uk.nhs.ciao.transport.spine.multipart;

import java.io.ByteArrayInputStream;
import java.util.Enumeration;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.www.mime.MimeHeaders;
import org.w3c.www.mime.MimeHeadersFactory;
import org.w3c.www.mime.MimeParser;

import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

/**
 * Represents a part/entity contained within a multipart body
 * <p>
 * The entity is made up of a series of name/value headers and a body.
 */
public class Part {
	public static final String CONTENT_ID = "Content-Id";
	public static final String CONTENT_TYPE = "Content-Type";
	public static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Part.class);
	private static final MimeHeadersFactory MIME_HEADERS_FACTORY = new MimeHeadersFactory();
	private static final String CRLF = "\r\n";
	private static final String HEADER_SEPARATOR = ": ";
	private static final Pattern RAW_CONTENT_ID_PATTERN = Pattern.compile("\\A\\s*<(.*)>\\s*\\Z");
	
	/**
	 * Represents the name/value headers of a part.
	 * <p>
	 * Multiple values for the same name are allowed and names are case-insensitive (RFC 5234, 2045, 822).
	 */
	private final EntrySeries headers;
	
	/**
	 * The body / main content of the part.
	 * <p>
	 * Typically a string or character stream. When serialising the body,
	 * the object toString() is used.
	 */
	private Object body;
	
	public Part() {
		headers = new EntrySeries();
	}
	
	public Part(final Part part) {
		headers = new EntrySeries(part.headers);
		body = part.body;
	}
	
	public EntrySeries getHeaders() {
		return headers;
	}
	
	public String getRawContentId() {
		return headers.getFirstValue(CONTENT_ID);
	}
	
	public void setRawContentId(final String rawContentId) {
		headers.setOrRemove(CONTENT_ID, rawContentId);
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
		return headers.getFirstValue(CONTENT_TYPE);
	}
	
	public void setContentType(final String contentType) {
		headers.setOrRemove(CONTENT_TYPE, contentType);
	}
	
	public String getContentTransferEncoding() {
		return headers.getFirstValue(CONTENT_TRANSFER_ENCODING);
	}
	
	public void setContentTransferEncoding(final String contentTransferEncoding) {
		headers.setOrRemove(CONTENT_TRANSFER_ENCODING, contentTransferEncoding);
	}
	
	public Object getBody() {
		return body;
	}
	
	public void setBody(final Object body) {
		this.body = body;
	}
	
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();		
		toString(builder);		
		return builder.toString();
	}
	
	public void toString(final StringBuilder builder) {
		for (final Entry<String, String> entry: headers) {
			builder.append(entry.getKey())
				.append(HEADER_SEPARATOR)
				.append(entry.getValue())
				.append(CRLF);
		}
		builder.append(CRLF);
		
		if (body != null) {
			builder.append(body);
		}
	}
	
	public static Part parse(final String entity) {
		final Part part = new Part();
		
		ByteArrayInputStream in = null;
		try {
			in = new ByteArrayInputStream(entity.getBytes());
			final MimeParser parser = new MimeParser(in, MIME_HEADERS_FACTORY);
			final MimeHeaders mimeHeaders = (MimeHeaders)parser.parse(); // cast is safe
			
			@SuppressWarnings("unchecked")
			final Enumeration<String> enumeration = mimeHeaders.enumerateHeaders();
			if (enumeration != null) {
				while (enumeration.hasMoreElements()) {
					final String name = enumeration.nextElement();
					part.getHeaders().add(name, mimeHeaders.getValue(name));
				}
			}
			
			final byte[] bytes = ByteStreams.toByteArray(in);
			part.setBody(new String(bytes));
		} catch (Exception e) {
			LOGGER.debug("Unable to parse MIME headers on multipart Part", e);
			throw Throwables.propagate(e);
		} finally {
			Closeables.closeQuietly(in);
		}
		
		return part;
	}
}
