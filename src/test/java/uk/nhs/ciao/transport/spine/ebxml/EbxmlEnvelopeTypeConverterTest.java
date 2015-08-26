package uk.nhs.ciao.transport.spine.ebxml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.nhs.ciao.camel.CamelUtils;

import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;

/**
 * Tests for {@link EbxmlEnvelopeTypeConverter}
 */
public class EbxmlEnvelopeTypeConverterTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(EbxmlEnvelopeTypeConverterTest.class);
	
	private CamelContext context;
	private ProducerTemplate producerTemplate;
	
	@Before
	public void setup() throws Exception {
		context = new DefaultCamelContext();
		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				from("direct:deserialize")
					.streamCaching()
					.log(LoggingLevel.INFO, LOGGER, "${body}")
					.convertBodyTo(EbxmlEnvelope.class);
			}
		});
		
		context.start();
		producerTemplate = new DefaultProducerTemplate(context);
		producerTemplate.start();
	}
	
	@After
	public void teardown() {
		CamelUtils.stopQuietly(producerTemplate, context);
	}
	
	/**
	 * Tests that an input stream can be converted to an envelope (the standard
	 * conversion method is tested)
	 */
	@Test
	public void testConvertFromInputStream() throws IOException {
		final InputStream in = getClass().getResourceAsStream("./test-manifest.xml");
		try {
			Assert.assertNotNull(deserialize(in));
		} finally {
			Closeables.closeQuietly(in);
		}
	}
	
	/**
	 * Tests that a string (i.e. something other than input stream) can be converted to an envelope (the fallback / intermediate
	 * conversion method is tested)
	 */
	@Test
	public void testConvertFromString() throws IOException {
		final InputStream in = getClass().getResourceAsStream("./test-ack.xml");
		Reader reader = null;
		try {
			reader = new InputStreamReader(in);
			final String body = CharStreams.toString(reader);
			Assert.assertNotNull(deserialize(body));
		} finally {
			Closeables.closeQuietly(in);
		}
	}
	
	/**
	 * Converts the body to an {@link EbxmlEnvelope} by sending into through
	 * the configured camel route
	 */
	private EbxmlEnvelope deserialize(final Object body) {
		final Exchange exchange = new DefaultExchange(context);
		exchange.setPattern(ExchangePattern.InOut);
		exchange.getIn().setBody(body);
		
		producerTemplate.send("direct:deserialize", exchange);
		return exchange.getOut().getBody(EbxmlEnvelope.class);
	}
}
