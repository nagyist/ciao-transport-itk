package uk.nhs.ciao.transport.spine.multipart;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.field.LenientFieldParser;
import org.apache.james.mime4j.message.SimpleContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
			
			final MimeConfig config = new MimeConfig();
			final LenientFieldParser fieldParser = new LenientFieldParser();
			final MimeStreamParser parser = new MimeStreamParser(config);
			
			final SimpleContentHandler contentHandler = new SimpleContentHandler(fieldParser, DecodeMonitor.SILENT) {				
				@Override
				public void headers(final Header header) {
					for (final Field field: header.getFields()) {
						part.getHeaders().add(field.getName(), field.getBody());
					}
				}
				
				@Override
				public void body(BodyDescriptor bd, InputStream is)
						throws MimeException, IOException {
					System.out.println(bd.getMimeType());
					final byte[] bytes = ByteStreams.toByteArray(is);
					part.setBody(new String(bytes));
				}
			};
			parser.setContentHandler(contentHandler);
			parser.parse(in);
		} catch (Exception e) {
			LOGGER.debug("Unable to parse MIME headers on multipart Part", e);
			throw Throwables.propagate(e);
		} finally {
			Closeables.closeQuietly(in);
		}
		
		return part;
	}
}
