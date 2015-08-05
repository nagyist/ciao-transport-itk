package uk.nhs.ciao.transport.spine.hl7;

import java.io.InputStream;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

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
import org.unitils.reflectionassert.ReflectionAssert;

import com.google.common.io.Closeables;

/**
 * Tests for {@link HL7PartTypeConverter}
 */
public class Hl7PartTypeConverterTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(Hl7PartTypeConverterTest.class);
	
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
					.convertBodyTo(HL7Part.class);
				
				from("direct:serialize")
					.streamCaching()
					.log(LoggingLevel.INFO, LOGGER, "${body}")
					.convertBodyTo(String.class);
			}
		});
		
		context.start();
		producerTemplate = new DefaultProducerTemplate(context);
		producerTemplate.start();
	}
	
	@After
	public void teardown() {
		if (producerTemplate != null) {
			try {
				producerTemplate.stop();
			} catch (Exception e) {
				LOGGER.warn("Unable to stop producerTemplate", e);
			}
		}
		
		if (context != null) {
			try {
				context.stop();
			} catch (Exception e) {
				LOGGER.warn("Unable to stop context", e);
			}
		}
	}
	
	@Test
	public void testExampleHl7Part() {
		assertRoundtrip("test-hl7.xml");
	}
	
	@Test
	public void testHl7DateFormat() throws ParseException {
		final HL7Part part = loadResource("test-hl7.xml");
		final Calendar calendar = Calendar.getInstance(); // HL7 uses local timezone
		calendar.clear();
		calendar.set(2015, Calendar.JUNE, 3, 8, 27, 36);
		final Date expected = calendar.getTime();
		final Date actual = part.getCreationTimeAsDate();
		
		Assert.assertEquals("CreationDate", expected, actual);
	}
	
	private final HL7Part loadResource(final String name) {
		final InputStream in = getClass().getResourceAsStream(name);
		try {
			Assert.assertNotNull("resource: " + name, in);
			final HL7Part part = context.getTypeConverter().convertTo(HL7Part.class, in);
			Assert.assertNotNull("HL7Part", part);
			return part;
		} finally {
			Closeables.closeQuietly(in);
		}
	}
	
	private void assertRoundtrip(final String name) {
		final HL7Part expected = loadResource(name);
		assertRoundtrip(expected);
	}
	
	private void assertRoundtrip(final HL7Part expected) {
		final String xml = serialize(expected);
		final HL7Part actual = deserialize(xml);
		ReflectionAssert.assertReflectionEquals(expected, actual);
	}
	
	/**
	 * Converts the part to a String by sending into through
	 * the configured camel route
	 */
	private String serialize(final HL7Part body) {
		final Exchange exchange = new DefaultExchange(context);
		exchange.setPattern(ExchangePattern.InOut);
		exchange.getIn().setBody(body);
		
		producerTemplate.send("direct:serialize", exchange);
		return exchange.getOut().getBody(String.class);
	}
	
	/**
	 * Converts the body to an {@link HL7Part} by sending into through
	 * the configured camel route
	 */
	private HL7Part deserialize(final Object body) {
		final Exchange exchange = new DefaultExchange(context);
		exchange.setPattern(ExchangePattern.InOut);
		exchange.getIn().setBody(body);
		
		producerTemplate.send("direct:deserialize", exchange);
		return exchange.getOut().getBody(HL7Part.class);
	}
}
