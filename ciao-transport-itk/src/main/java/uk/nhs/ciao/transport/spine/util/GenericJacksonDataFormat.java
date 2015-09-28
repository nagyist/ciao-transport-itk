package uk.nhs.ciao.transport.spine.util;

import java.io.InputStream;
import java.util.HashMap;

import org.apache.camel.Exchange;
import org.apache.camel.component.jackson.JacksonDataFormat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A {@link JacksonDataFormat} which can handle generic type details
 * via a specified {@link TypeReference}
 */
public class GenericJacksonDataFormat extends JacksonDataFormat {
	private TypeReference<?> unmarshalTypeReference;
	
	public GenericJacksonDataFormat() {
		super();
	}

	public GenericJacksonDataFormat(final TypeReference<?> unmarshalTypeReference) {
		super();
		
		this.unmarshalTypeReference = unmarshalTypeReference;
	}

	public GenericJacksonDataFormat(final ObjectMapper mapper, final TypeReference<?> unmarshalTypeReference) {
		super(mapper, HashMap.class);
		
		this.unmarshalTypeReference = unmarshalTypeReference;
	}

	public TypeReference<?> getUnmarshalTypeReference() {
		return unmarshalTypeReference;
	}
	
	public void setUnmarshalTypeReference(final TypeReference<?> unmarshalTypeReference) {
		this.unmarshalTypeReference = unmarshalTypeReference;
	}

	@Override
	public Object unmarshal(final Exchange exchange, final InputStream stream)
			throws Exception {
		if (unmarshalTypeReference != null) {
			return getObjectMapper().readValue(stream, unmarshalTypeReference);
		}
		
		return super.unmarshal(exchange, stream);
	}
}
