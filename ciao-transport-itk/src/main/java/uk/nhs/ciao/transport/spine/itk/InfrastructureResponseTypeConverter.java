package uk.nhs.ciao.transport.spine.itk;

import static uk.nhs.ciao.transport.spine.util.TypeConverterHelper.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.FallbackConverter;
import org.apache.camel.spi.TypeConverterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.io.Closeables;

/**
 * Camel type converters to convert to/from InfrastructureResponse.
 * <p>
 * The converters are automatically registered via Camel's type converter META-INF/services file:
 * <code>/META-INF/services/org/apache/camel/TypeConverter</code>
 */
@Converter
public final class InfrastructureResponseTypeConverter {
	private static final Logger LOGGER = LoggerFactory.getLogger(InfrastructureResponseTypeConverter.class);
	
	/**
	 * Holds a single parser instance per thread (the parsers are not thread-safe)
	 */
	private static final ThreadLocal<InfrastructureResponseParser> PARSER = new ThreadLocal<InfrastructureResponseParser>() {
		@Override
		protected InfrastructureResponseParser initialValue() {
			try {
				return new InfrastructureResponseParser();
			} catch (Exception e) {
				LOGGER.error("Unable to create InfrastructureResponseParser", e);
				throw Throwables.propagate(e);
			}
		}
	};

	/**
	 * Holds a single serializer instance across all threads (lazy-loaded)
	 */
	private static AtomicReference<InfrastructureResponseSerializer> SERIALIZER = new AtomicReference<InfrastructureResponseSerializer>();
	
	private InfrastructureResponseTypeConverter() {
		// Suppress default constructor
	}
	
	/**
	 * Converts the specified input stream to a InfrastructureResponse
	 * <p>
	 * The InputStream is not closed by this method.
	 */
	@Converter
	public static InfrastructureResponse fromInputStream(final InputStream in) throws IOException {
		LOGGER.debug("fromInputStream()");
		
		final InfrastructureResponseParser parser = PARSER.get();
		return parser.parse(in);
	}
	
	/**
	 * Encodes the envelope as an XML string (via a freemarker template)
	 * 
	 * @throws Exception If the envelope could not be encoded
	 */
	@Converter
	public static String toString(final InfrastructureResponse envelope) throws IOException {
		if (envelope == null) {
			return null;
		}
		
		return getSerializer().serialize(envelope);
	}
	
	/**
	 * Camel fallback converter to convert a value to InfrastructureResponse to a specified type either directly or via InputStream as an intermediate.
	 * <p>
	 * The type converter registry is used to convert the value to InputStream.
	 */
	@FallbackConverter
	public static <T> T convertToInfrastructureResponse(final Class<T> type, final Exchange exchange, final Object value, final TypeConverterRegistry registry) throws IOException {
		if (!InfrastructureResponse.class.equals(type)) {
			// Only handle InfrastructureResponse conversions
			return null;
		} else if (value instanceof InfrastructureResponse) {
			// No conversion required
			return type.cast(value);
		}
		
		LOGGER.debug("convertToInfrastructureResponse via (InputStream) from: {}", value.getClass());
		
		// Convert via InputStream
		final InputStream in = castOrConvert(InputStream.class, exchange, value, registry);
		try {
			return in == null ? null : type.cast(fromInputStream(in));
		} finally {
			// close the stream if it is an intermediate
			// does the stream always need to be closed? will camel close the stream when in == value?
			if (in != value) {
				Closeables.closeQuietly(in);
			}
		}
	}
	
	/**
	 * Camel fallback converter to convert a InfrastructureResponse to a specified type either directly or via String as an intermediate.
	 * <p>
	 * The type converter registry is used to convert the InfrastructureResponse to String
	 */
	@FallbackConverter
	public static <T> T convertFromInfrastructureResponse(final Class<T> type, final Exchange exchange, final Object value, final TypeConverterRegistry registry) throws IOException {
		if (!(value instanceof InfrastructureResponse)) {
			// Only handle envelope conversions
			return null;
		} else if (InfrastructureResponse.class.isAssignableFrom(type)) {
			// No conversion required
			return type.cast(value);
		} else if (!canConvert(byte[].class, type, registry)) {
			// Can only support conversions via byte array as intermediate
			return null;
		}
		
		LOGGER.debug("convertFromInfrastructureResponse via (String) to: {}", type);
		
		// Convert via String
		final String string = toString((InfrastructureResponse)value);		
		return castOrConvert(type, exchange, string, registry);
	}
		
	/**
	 * Lazy loads the single serializer instance
	 */
	private static InfrastructureResponseSerializer getSerializer() throws IOException {
		InfrastructureResponseSerializer serializer = SERIALIZER.get();
		
		// lazy-load
		if (serializer == null) {
			serializer = new InfrastructureResponseSerializer();
			if (!SERIALIZER.compareAndSet(null, serializer)) {
				// some other thread got there first - use their instance
				serializer = SERIALIZER.get();
			}
		}
		
		return serializer;
	}
}
