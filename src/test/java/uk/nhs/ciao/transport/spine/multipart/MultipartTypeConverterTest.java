package uk.nhs.ciao.transport.spine.multipart;

import static org.junit.Assert.*;

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
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;

/**
 * Tests for {@link MultipartTypeConverter}
 */
public class MultipartTypeConverterTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(MultipartTypeConverterTest.class);
	
	private CamelContext context;
	private ProducerTemplate producerTemplate;
	private String contentType;
	private String boundary;
	private String start;
	
	@Before
	public void setup() throws Exception {
		context = new DefaultCamelContext();
		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				from("direct:deserialize")
					.streamCaching()
					.log(LoggingLevel.INFO, LOGGER, "${body}")
					.convertBodyTo(MultipartBody.class);
				
				from("direct:serialize")
					.streamCaching()
					.convertBodyTo(String.class)
					.log("${body}");
			}
		});
		
		context.start();
		producerTemplate = new DefaultProducerTemplate(context);
		producerTemplate.start();
		
		// content-type / boundary used in test files
		boundary = "--=_MIME-Boundary";
		start = "<80c4eaee-db6a-4113-99dd-a6bd4d9380e7>";
		contentType = "multipart/related; boundary=\"--=_MIME-Boundary\"; type=\"text/xml\"; start=\"<80c4eaee-db6a-4113-99dd-a6bd4d9380e7>\"";
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
	
	/**
	 * Tests that an input stream can be converted to a multi-part body (the standard
	 * conversion method is tested)
	 */
	@Test
	public void testConvertFromInputStream() throws IOException {
		final InputStream in = getClass().getResourceAsStream("./test-trunk.multipart");
		try {
			final MultipartBody multipartBody = deserialize(in);
			assertMultipartTrunkRequest(multipartBody);
		} finally {
			Closeables.closeQuietly(in);
		}
	}
	
	/**
	 * Tests that a string (i.e. something other than input stream) can be converted to a multi-part body (the fallback / intermediate
	 * conversion method is tested)
	 */
	@Test
	public void testConvertFromString() throws IOException {
		final InputStream in = getClass().getResourceAsStream("./test-trunk.multipart");
		Reader reader = null;
		try {
			reader = new InputStreamReader(in);
			final String body = CharStreams.toString(reader);
			
			final MultipartBody multipartBody = deserialize(body);
			assertMultipartTrunkRequest(multipartBody);
		} finally {
			Closeables.closeQuietly(in);
		}
	}
	
	/**
	 * Tests that a multi-part body can be converted to a string (the fallback / intermediate conversion method and standard
	 * byte[] method are tested)
	 */
	@Test
	public void testConvertToString() throws IOException {
		final MultipartBody body = new MultipartBody();
		body.setBoundary(boundary);
		body.addPart("text/xml", "<root>the first part</root>").setRawContentId(start);
		body.addPart("text/plain", "the second part");
		
		final String serializedBody = serialize(body);
		assertNotNull(serializedBody);
		assertTrue(!serializedBody.isEmpty());
		
		// Rountrip for testing of content
		final MultipartBody actual = deserialize(serializedBody);
		assertNotNull(actual);		
		assertEquals(2, actual.getParts().size());
		assertEquals("<root>the first part</root>", actual.getParts().get(0).getBody(String.class));
		assertEquals("the second part", actual.getParts().get(1).getBody(String.class));
	}
	
	/**
	 * Asserts the multi-part body contains values matching the 'trunk-test.multipart' fixture
	 */
	private void assertMultipartTrunkRequest(final MultipartBody multipartBody) {
		assertNotNull(multipartBody);
		assertEquals("boundary", boundary, multipartBody.getBoundary());
		assertEquals("boundary", "", multipartBody.getPreamble());
		assertEquals("boundary", "", multipartBody.getEpilogue());
		
		assertEquals("number of parts", 3, multipartBody.getParts().size());
		
		final Part ebxmlPart = multipartBody.getParts().get(0);
		assertEquals("contentType", "text/xml", ebxmlPart.getContentType());
		assertEquals("contentId", "80c4eaee-db6a-4113-99dd-a6bd4d9380e7", ebxmlPart.getContentId());
		assertEquals("contentTransferEncoding", "8bit", ebxmlPart.getContentTransferEncoding());
	}
	
	/**
	 * Converts the body to an {@link MultipartBody} by sending into through
	 * the configured camel route
	 */
	private MultipartBody deserialize(final Object body) {
		final Exchange exchange = new DefaultExchange(context);
		exchange.setPattern(ExchangePattern.InOut);
		exchange.getIn().setBody(body);
		exchange.getIn().setHeader(Exchange.CONTENT_TYPE, contentType);
		
		producerTemplate.send("direct:deserialize", exchange);
		return exchange.getOut().getBody(MultipartBody.class);
	}
	
	/**
	 * Converts the {@link MultipartBody} to a string by sending into through
	 * the configured camel route
	 */
	private String serialize(final MultipartBody body) {
		final Exchange exchange = new DefaultExchange(context);
		exchange.setPattern(ExchangePattern.InOut);
		exchange.getIn().setBody(body);
		
		producerTemplate.send("direct:serialize", exchange);
		return exchange.getOut().getBody(String.class);
	}
}
