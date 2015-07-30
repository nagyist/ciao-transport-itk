package uk.nhs.ciao.transport.spine.ebxml;

import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unitils.reflectionassert.ReflectionAssert;

import com.google.common.io.Closeables;

/**
 * Tests for the {@link EbxmlEnvelope} serialization template
 */
public class EbxmlEnvelopeTemplateTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(EbxmlEnvelopeParserTest.class);
	
	private CamelContext context;
	private EbxmlEnvelopeParser parser;
	private EbxmlEnvelopeSerializer serializer;
	
	@Before
	public void setup() throws Exception {
		context = new DefaultCamelContext();
		context.start();
		
		this.parser = new EbxmlEnvelopeParser();
		this.serializer = new EbxmlEnvelopeSerializer();
	}
	
	@After
	public void tearDown() {
		if (context != null) {
			try {
				context.stop();
			} catch (Exception e) {
				LOGGER.warn("Unable to stop context", e);
			}
		}
	}
	
	@Test
	public void testSerializeAck() throws Exception {
		assertRoundtrip("./test-ack.xml");
	}
	
	@Test
	public void testSerializeError() throws Exception {
		assertRoundtrip("./test-error.xml");
	}
	
	@Test
	public void testSerializeManifest() throws Exception {
		assertRoundtrip("./test-manifest.xml");
	}
	
	private void assertRoundtrip(final String resourceName) throws Exception {
		// Parse the named example file
		EbxmlEnvelope expected = null;
		InputStream in = getClass().getResourceAsStream(resourceName);
		try {
			expected = parser.parse(in);
		} finally {
			Closeables.closeQuietly(in);
		}
		
		// Serialize the envelope back into XML (via the freemarker template)		
		final String xml = serializer.serialize(expected);
		
		// Parse the generate XML back into a bean
		EbxmlEnvelope actual = null;
		in = context.getTypeConverter().convertTo(InputStream.class, xml);
		try {
			actual = parser.parse(in);
		} finally {
			Closeables.closeQuietly(in);
		}
		
		// Check that the beans contain the same values
		ReflectionAssert.assertReflectionEquals(expected, actual);
	}
}
