package uk.nhs.ciao.transport.spine.multipart;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeTokenStream;
import org.apache.james.mime4j.stream.RecursionMode;

import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

/**
 * Instances of this class are NOT thread-safe
 */
public class MultipartParser {
	private final MimeTokenStream tokens;
	
	public MultipartParser() {
		this.tokens = new MimeTokenStream();
		this.tokens.setRecursionMode(RecursionMode.M_NO_RECURSE);
	}
	
	public MultipartBody parse(final String contentType, final InputStream in) throws IOException {
		final Exchange exchange = null;
		return parse(contentType, exchange, in);
	}
	
	public MultipartBody parse(final String contentType, final Exchange exchange,
			final InputStream in) throws IOException {
		tokens.parseHeadless(in, contentType);
		try {
			return toMultipartBody(exchange);
		} catch (MimeException e) {
			throw new IOException("Unable to parse content as MIME stream", e);
		}
	}
	
	public void parse(final Message in, final Message out) throws IOException {
		final String contentType = in.getHeader(Exchange.CONTENT_TYPE, String.class);
		final InputStream inputStream = in.getBody(InputStream.class);
		try {
			final MultipartBody body = parse(contentType, out.getExchange(), inputStream);
			out.setBody(body);
		} finally {
			Closeables.closeQuietly(inputStream);
		}
	}
	
	private MultipartBody toMultipartBody(final Exchange exchange) throws IOException, MimeException {
		final MultipartBody body = new MultipartBody();
		
		Part part = null;
		
		for (EntityState state = tokens.getState(); state != EntityState.T_END_OF_STREAM; state = tokens.next()) {
			switch (state) {
			case T_START_MULTIPART:
				final String boundary = tokens.getBodyDescriptor().getBoundary();
				body.setBoundary(boundary);				
				break;
			
			case T_PREAMBLE:
				body.setPreamble(readDecodedContent());
				break;
				
			case T_START_BODYPART:
				part = new Part();
				part.setExchange(exchange);
				
				break;
			case T_FIELD:
				final Field field = tokens.getField();
				part.addHeader(field.getName(), field.getBody());
				break;
				
			case T_BODY:
				part.setBody(readDecodedContent());
				break;
				
			case T_END_BODYPART:
				body.addPart(part);
				part = null;
				break;
				
			case T_EPILOGUE:
				body.setEpilogue(readDecodedContent());
				break;

			default:
				break;
			}
		}
		
		return body;
	}
	
	private String readDecodedContent() throws IOException {
		final InputStream in = tokens.getDecodedInputStream();
		try {
			final byte[] bytes = ByteStreams.toByteArray(in);
			return new String(bytes);
		} finally {
			Closeables.closeQuietly(in);
		}
	}
}
