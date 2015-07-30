package uk.nhs.ciao.transport.spine.util;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.TypeConverterRegistry;

/**
 * Helper methods for {@link TypeConverter}s
 */
public final class TypeConverterHelper {
	private TypeConverterHelper() {
		// Suppress default constructor
	}
	
	/**
	 * Helper method to coerce the specified value into the required type.
	 * <p>
	 * Value is cast if it is already of the correct kind, otherwise a camel type converter is tried. Null is
	 * returned if the value cannot be coerced.
	 */
	public static <T> T castOrConvert(final Class<T> toType, final Exchange exchange, final Object value, final TypeConverterRegistry registry) {
		final T result;
		
		if (toType.isInstance(value)) {
			result = toType.cast(value);
		} else {
			final TypeConverter typeConverter = registry.lookup(toType, value.getClass());
			result = typeConverter == null ? null : typeConverter.convertTo(toType, exchange, value);
		}
		
		return result;
	}
	
	/**
	 * Helper methods to test if a conversion can take place from one class to another - either via a direct cast, or
	 * via a registered Camel type converter.
	 */
	public static boolean canConvert(final Class<?> fromType, final Class<?> toType, final TypeConverterRegistry registry) {
		return toType.isAssignableFrom(fromType) || registry.lookup(toType, fromType) != null;
	}
}
