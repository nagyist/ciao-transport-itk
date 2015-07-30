package uk.nhs.ciao.transport.spine.ebxml;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.FallbackConverter;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.spi.TypeConverterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.io.Closeables;

/**
 * Camel type converters to convert <strong>to</strong> EbxmlEnvelope
 * <p>
 * To convert an EbxmlEnvelope to XML, a route of
 * <code>to("freemarker:uk/nhs/ciao/transport/spine/ebxml/ebxmlEnvelope.ftl")</code> can be used.
 * <p>
 * The converters are automatically registered via Camel's type converter META-INF/services file:
 * <code>/META-INF/services/org/apache/camel/TypeConverter</code>
 */
@Converter
public class EbxmlEnvelopeTypeConverter {
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
	 * Encodes the envelope as an XML string (via a freemarker template route)
	 * 
	 * @throws Exception If the envelope could not be encoded
	 */
	public static String toString(final ProducerTemplate producerTemplate, final EbxmlEnvelope envelope) throws Exception {
		if (envelope == null) {
			return null;
		}
		
		final Exchange exchange = new DefaultExchange(producerTemplate.getCamelContext());
		exchange.setPattern(ExchangePattern.InOut);
		exchange.getIn().setBody(envelope);
		producerTemplate.send("freemarker:uk/nhs/ciao/transport/spine/ebxml/ebxmlEnvelope.ftl", exchange);
		
		if (exchange.getException() != null) {
			throw exchange.getException();
		}
		
		return exchange.getOut().getMandatoryBody(String.class);
	}
	
	/**
	 * Helper method to coerce the specified value into the required type.
	 * <p>
	 * Value is cast if it is already of the correct kind, otherwise a camel type converter is tried. Null is
	 * returned if the value cannot be coerced.
	 */
	private static <T> T castOrConvert(final Class<T> toType, final Exchange exchange, final Object value, final TypeConverterRegistry registry) {
		final T result;
		
		if (toType.isInstance(value)) {
			result = toType.cast(value);
		} else {
			final TypeConverter typeConverter = registry.lookup(toType, value.getClass());
			result = typeConverter == null ? null : typeConverter.convertTo(toType, exchange, value);
		}
		
		return result;
	}
}
