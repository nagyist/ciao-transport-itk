package uk.nhs.ciao.transport.spine.ebxml;

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
 * Camel type converters to convert to/from EbxmlEnvelope.
 * <p>
 * The converters are automatically registered via Camel's type converter META-INF/services file:
 * <code>/META-INF/services/org/apache/camel/TypeConverter</code>
 */
@Converter
public final class EbxmlEnvelopeTypeConverter {
	private static final Logger LOGGER = LoggerFactory.getLogger(EbxmlEnvelopeTypeConverter.class);
	
	/**
	 * Holds a single parser instance per thread (the parsers are not thread-safe)
	 */
	private static final ThreadLocal<EbxmlEnvelopeParser> PARSER = new ThreadLocal<EbxmlEnvelopeParser>() {
		@Override
		protected EbxmlEnvelopeParser initialValue() {
			try {
				return new EbxmlEnvelopeParser();
			} catch (Exception e) {
				LOGGER.error("Unable to create EbxmlEnvelopeParser", e);
				throw Throwables.propagate(e);
			}
		}
	};

	/**
	 * Holds a single serializer instance across all threads (lazy-loaded)
	 */
	private static AtomicReference<EbxmlEnvelopeSerializer> SERIALIZER = new AtomicReference<EbxmlEnvelopeSerializer>();
	
	private EbxmlEnvelopeTypeConverter() {
		// Suppress default constructor
	}
	
	/**
	 * Converts the specified input stream to a EbxmlEnvelope
	 * <p>
	 * The InputStream is not closed by this method.
	 */
	@Converter
	public static EbxmlEnvelope fromInputStream(final InputStream in) throws IOException {
		LOGGER.debug("fromInputStream()");
		
		final EbxmlEnvelopeParser parser = PARSER.get();
		return parser.parse(in);
	}
	
	/**
	 * Encodes the envelope as an XML string (via a freemarker template)
	 * 
	 * @throws Exception If the envelope could not be encoded
	 */
	@Converter
	public static String toString(final EbxmlEnvelope envelope) throws IOException {
		if (envelope == null) {
			return null;
		}
		
		return getSerializer().serialize(envelope);
	}
	
	/**
	 * Camel fallback converter to convert a value to EbxmlEnvelope to a specified type either directly or via InputStream as an intermediate.
	 * <p>
	 * The type converter registry is used to convert the value to InputStream.
	 */
	@FallbackConverter
	public static <T> T convertToEbxmlEnvelope(final Class<T> type, final Exchange exchange, final Object value, final TypeConverterRegistry registry) throws IOException {
		if (!EbxmlEnvelope.class.equals(type)) {
			// Only handle EbxmlEnvelope conversions
			return null;
		} else if (value instanceof EbxmlEnvelope) {
			// No conversion required
			return type.cast(value);
		}
		
		LOGGER.debug("convertToEbxmlEnvelope via (InputStream) from: {}", value.getClass());
		
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
	 * Camel fallback converter to convert a EbxmlEnvelope to a specified type either directly or via String as an intermediate.
	 * <p>
	 * The type converter registry is used to convert the EbxmlEnvelope to String
	 */
	@FallbackConverter
	public static <T> T convertFromEbxmlEnvelop(final Class<T> type, final Exchange exchange, final Object value, final TypeConverterRegistry registry) throws IOException {
		if (!(value instanceof EbxmlEnvelope)) {
			// Only handle envelope conversions
			return null;
		} else if (EbxmlEnvelope.class.isAssignableFrom(type)) {
			// No conversion required
			return type.cast(value);
		} else if (!canConvert(byte[].class, type, registry)) {
			// Can only support conversions via byte array as intermediate
			return null;
		}
		
		LOGGER.debug("convertFromEbxmlEnvelope via (String) to: {}", type);
		
		// Convert via String
		final String string = toString((EbxmlEnvelope)value);		
		return castOrConvert(type, exchange, string, registry);
	}
		
	/**
	 * Lazy loads the single serializer instance
	 */
	private static EbxmlEnvelopeSerializer getSerializer() throws IOException {
		EbxmlEnvelopeSerializer serializer = SERIALIZER.get();
		
		// lazy-load
		if (serializer == null) {
			serializer = new EbxmlEnvelopeSerializer();
			if (!SERIALIZER.compareAndSet(null, serializer)) {
				// some other thread got there first - use their instance
				serializer = SERIALIZER.get();
			}
		}
		
		return serializer;
	}
}
