package uk.nhs.ciao.transport.spine.multipart;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;

import com.google.common.io.Closeables;

@Converter
public class MultipartTypeConverter {
	private static final ThreadLocal<MultipartParser> PARSER = new ThreadLocal<MultipartParser>() {
		@Override
		protected MultipartParser initialValue() {
			return new MultipartParser();
		}
	};
	
	@Converter
	public static MultipartBody fromInputStream(final InputStream in, final Exchange exchange) throws IOException {
		final String contentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);		
		final MultipartParser parser = PARSER.get();
		return parser.parse(contentType, exchange, in);
	}
	
	@Converter
	public static byte[] toByteArray(final MultipartBody body) throws IOException {
		ByteArrayOutputStream out = null;
		try {
			out = new ByteArrayOutputStream();
			body.write(out);
			out.flush();
			return out.toByteArray();
		} finally {
			Closeables.close(out, true);
		}
	}
	
	// TODO: Add FallbackConverters to handle types other than InputStream/byte[] via the registered Camel types
	// can call the existing to/from methods on this class
}
