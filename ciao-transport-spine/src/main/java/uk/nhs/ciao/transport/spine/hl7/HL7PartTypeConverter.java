package uk.nhs.ciao.transport.spine.hl7;

import static uk.nhs.ciao.transport.itk.util.TypeConverterHelper.*;

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
 * Camel type converters to convert to/from HL7Part.
 * <p>
 * The converters are automatically registered via Camel's type converter META-INF/services file:
 * <code>/META-INF/services/org/apache/camel/TypeConverter</code>
 */
@Converter
public final class HL7PartTypeConverter {
	private static final Logger LOGGER = LoggerFactory.getLogger(HL7PartTypeConverter.class);
	
	/**
	 * Holds a single parser instance per thread (the parsers are not thread-safe)
	 */
	private static final ThreadLocal<HL7PartParser> PARSER = new ThreadLocal<HL7PartParser>() {
		@Override
		protected HL7PartParser initialValue() {
			try {
				return new HL7PartParser();
			} catch (Exception e) {
				LOGGER.error("Unable to create HL7PartParser", e);
				throw Throwables.propagate(e);
			}
		}
	};

	/**
	 * Holds a single serializer instance across all threads (lazy-loaded)
	 */
	private static AtomicReference<HL7PartSerializer> SERIALIZER = new AtomicReference<HL7PartSerializer>();
	
	private HL7PartTypeConverter() {
		// Suppress default constructor
	}
	
	/**
	 * Converts the specified input stream to a HL7Part
	 * <p>
	 * The InputStream is not closed by this method.
	 */
	@Converter
	public static HL7Part fromInputStream(final InputStream in) throws IOException {
		LOGGER.debug("fromInputStream()");
		
		final HL7PartParser parser = PARSER.get();
		return parser.parse(in);
	}
	
	/**
	 * Encodes the part as an XML string (via a freemarker template)
	 * 
	 * @throws Exception If the part could not be encoded
	 */
	@Converter
	public static String toString(final HL7Part part) throws IOException {
		if (part == null) {
			return null;
		}
		
		return getSerializer().serialize(part);
	}
	
	/**
	 * Camel fallback converter to convert a value to HL7Part to a specified type either directly or via InputStream as an intermediate.
	 * <p>
	 * The type converter registry is used to convert the value to InputStream.
	 */
	@FallbackConverter
	public static <T> T convertToHL7Part(final Class<T> type, final Exchange exchange, final Object value, final TypeConverterRegistry registry) throws IOException {
		if (!HL7Part.class.equals(type)) {
			// Only handle HL7Part conversions
			return null;
		} else if (value instanceof HL7Part) {
			// No conversion required
			return type.cast(value);
		}
		
		LOGGER.debug("convertToHL7Part via (InputStream) from: {}", value.getClass());
		
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
	 * Camel fallback converter to convert a HL7Part to a specified type either directly or via String as an intermediate.
	 * <p>
	 * The type converter registry is used to convert the HL7Part to String
	 */
	@FallbackConverter
	public static <T> T convertFromHL7Part(final Class<T> type, final Exchange exchange, final Object value, final TypeConverterRegistry registry) throws IOException {
		if (!(value instanceof HL7Part)) {
			// Only handle HL7Part conversions
			return null;
		} else if (HL7Part.class.isAssignableFrom(type)) {
			// No conversion required
			return type.cast(value);
		} else if (!canConvert(byte[].class, type, registry)) {
			// Can only support conversions via byte array as intermediate
			return null;
		}
		
		LOGGER.debug("convertFromHL7Part via (String) to: {}", type);
		
		// Convert via String
		final String string = toString((HL7Part)value);		
		return castOrConvert(type, exchange, string, registry);
	}
		
	/**
	 * Lazy loads the single serializer instance
	 */
	private static HL7PartSerializer getSerializer() throws IOException {
		HL7PartSerializer serializer = SERIALIZER.get();
		
		// lazy-load
		if (serializer == null) {
			serializer = new HL7PartSerializer();
			if (!SERIALIZER.compareAndSet(null, serializer)) {
				// some other thread got there first - use their instance
				serializer = SERIALIZER.get();
			}
		}
		
		return serializer;
	}
}
