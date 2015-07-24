package uk.nhs.ciao.transport.spine.multipart;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeTokenStream;
import org.apache.james.mime4j.stream.RecursionMode;

import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

/**
 * Parses a stream of bytes into a {@link MultipartBody}.
 * <p>
 * <strong>Instances of this class are NOT thread-safe</strong>
 */
public class MultipartParser {
	private final MimeTokenStream tokens;
	
	/**
	 * Constructs a new parser instance
	 */
	public MultipartParser() {
		this.tokens = new MimeTokenStream();
		this.tokens.setRecursionMode(RecursionMode.M_NO_RECURSE);
	}
	
	/**
	 * Parses the specified stream into a {@link MultipartBody}
	 * <p>
	 * Any nested multipart bodies are left as-is: i.e. a recursive parse is not
	 * performed and the body is stored in the same way as any other {@link Part}.
	 */
	public MultipartBody parse(final String contentType, final InputStream in) throws IOException {
		final Exchange exchange = null;
		return parse(contentType, exchange, in);
	}
	
	/**
	 * Parses the specified stream into a {@link MultipartBody}
	 * <p>
	 * Any nested multipart bodies are left as-is: i.e. a recursive parse is not
	 * performed and the body is stored in the same way as any other part.
	 * <p>
	 * If an exchange is specified, it is set as the exchange property on any created {@link Part}s.
	 */
	public MultipartBody parse(final String contentType, final Exchange exchange,
			final InputStream in) throws IOException {
		tokens.parseHeadless(in, contentType);
		try {
			return convertTokensToMultipartBody(exchange);
		} catch (MimeException e) {
			throw new IOException("Unable to parse content as MIME stream", e);
		}
	}
	
	/**
	 * Interprets the tokens until end of stream.
	 */
	private MultipartBody convertTokensToMultipartBody(final Exchange exchange) throws IOException, MimeException {
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
