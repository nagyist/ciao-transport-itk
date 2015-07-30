package uk.nhs.ciao.transport.spine.multipart;

import static uk.nhs.ciao.transport.spine.util.TypeConverterHelper.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.FallbackConverter;
import org.apache.camel.spi.TypeConverterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Closeables;

/**
 * Camel type converters to convert to/from MultipartBody
 * <p>
 * The converters are automatically registered via Camel's type converter META-INF/services file:
 * <code>/META-INF/services/org/apache/camel/TypeConverter</code>
 */
@Converter
public final class MultipartTypeConverter {
	private static final Logger LOGGER = LoggerFactory.getLogger(MultipartTypeConverter.class);
	
	/**
	 * Holds a single parser instance per thread (the parsers are not thread-safe)
	 */
	private static final ThreadLocal<MultipartParser> PARSER = new ThreadLocal<MultipartParser>() {
		@Override
		protected MultipartParser initialValue() {
			return new MultipartParser();
		}
	};
	
	private MultipartTypeConverter() {
		// Suppress default constructor
	}
	
	/**
	 * Converts the specified input stream to a MultipartBody
	 * <p>
	 * The InputStream is not closed by this method.
	 */
	@Converter
	public static MultipartBody fromInputStream(final InputStream in, final Exchange exchange) throws IOException {
		LOGGER.debug("fromInputStream()");
		
		final String contentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);		
		final MultipartParser parser = PARSER.get();
		return parser.parse(contentType, exchange, in);
	}
	
	/**
	 * Converts the specified MultipartBody into a byte array
	 */
	@Converter
	public static byte[] toByteArray(final MultipartBody body) throws IOException {
		LOGGER.debug("toByteArray()");
		
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
	
	/**
	 * Camel fallback converter to convert a value to MultipartBody to a specified type either directly or via InputStream as an intermediate.
	 * <p>
	 * The type converter registry is used to convert the value to InputStream.
	 */
	@FallbackConverter
	public static <T> T convertToMultipartBody(final Class<T> type, final Exchange exchange, final Object value, final TypeConverterRegistry registry) throws IOException {
		if (!MultipartBody.class.equals(type)) {
			// Only handle multipart conversions
			return null;
		} else if (value instanceof MultipartBody) {
			// No conversion required
			return type.cast(value);
		}
		
		LOGGER.debug("convertToMultipartBody via (InputStream) from: {}", value.getClass());
		
		// Convert via InputStream
		final InputStream in = castOrConvert(InputStream.class, exchange, value, registry);
		try {
			return in == null ? null : type.cast(fromInputStream(in, exchange));
		} finally {
			// close the stream if it is an intermediate
			// does the stream always need to be closed? will camel close the stream when in == value?
			if (in != value) {
				Closeables.closeQuietly(in);
			}
		}
	}
	
	/**
	 * Camel fallback converter to convert a MultipartBody to a specified type either directly or via byte[] as an intermediate.
	 * <p>
	 * The type converter registry is used to convert the MultipartBody to byte[].
	 */
	@FallbackConverter
	public static <T> T convertFromMultipartBody(final Class<T> type, final Exchange exchange, final Object value, final TypeConverterRegistry registry) throws IOException {
		if (!(value instanceof MultipartBody)) {
			// Only handle multipart conversions
			return null;
		} else if (MultipartBody.class.isAssignableFrom(type)) {
			// No conversion required
			return type.cast(value);
		} else if (!canConvert(byte[].class, type, registry)) {
			// Can only support conversions via byte array as intermediate
			return null;
		}
		
		LOGGER.debug("convertFromMultipartBody via (byte[]) to: {}", type);
		
		// Convert via byte[]
		final byte[] bytes = toByteArray((MultipartBody)value);		
		return castOrConvert(type, exchange, bytes, registry);
	}
}
