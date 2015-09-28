package uk.nhs.ciao.transport.itk.envelope;

import java.io.InputStream;

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
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unitils.reflectionassert.ReflectionAssert;

import uk.nhs.ciao.camel.CamelUtils;
import uk.nhs.ciao.transport.itk.envelope.InfrastructureResponse;
import uk.nhs.ciao.transport.itk.envelope.InfrastructureResponseTypeConverter;

/**
 * Tests for {@link InfrastructureResponseTypeConverter}
 */
public class InfrastructureResponseTypeConverterTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(InfrastructureResponseTypeConverterTest.class);
	
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
					.convertBodyTo(InfrastructureResponse.class);
				
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
		CamelUtils.stopQuietly(producerTemplate, context);
	}
	
	@Test
	public void textInfAck() {
		assertRoundtrip(getClass().getResourceAsStream("test-infack.xml"));
	}
	
	@Test
	public void textInfNack() {
		assertRoundtrip(getClass().getResourceAsStream("test-infnack.xml"));
	}
	
	private void assertRoundtrip(final InputStream in) {
		final InfrastructureResponse expected = context.getTypeConverter().convertTo(InfrastructureResponse.class, in);
		assertRoundtrip(expected);
	}
	
	private void assertRoundtrip(final InfrastructureResponse expected) {
		final String xml = serialize(expected);
		final InfrastructureResponse actual = deserialize(xml);
		ReflectionAssert.assertReflectionEquals(expected, actual);
	}
	
	/**
	 * Converts the envelope to a String by sending into through
	 * the configured camel route
	 */
	private String serialize(final InfrastructureResponse body) {
		final Exchange exchange = new DefaultExchange(context);
		exchange.setPattern(ExchangePattern.InOut);
		exchange.getIn().setBody(body);
		
		producerTemplate.send("direct:serialize", exchange);
		return exchange.getOut().getBody(String.class);
	}
	
	/**
	 * Converts the body to an {@link InfrastructureResponse} by sending into through
	 * the configured camel route
	 */
	private InfrastructureResponse deserialize(final Object body) {
		final Exchange exchange = new DefaultExchange(context);
		exchange.setPattern(ExchangePattern.InOut);
		exchange.getIn().setBody(body);
		
		producerTemplate.send("direct:deserialize", exchange);
		return exchange.getOut().getBody(InfrastructureResponse.class);
	}
}
