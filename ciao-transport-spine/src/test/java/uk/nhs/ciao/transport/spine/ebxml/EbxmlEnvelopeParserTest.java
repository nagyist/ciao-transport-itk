package uk.nhs.ciao.transport.spine.ebxml;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;
import org.unitils.reflectionassert.ReflectionAssert;

import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope.ManifestReference;

import com.google.common.io.Closeables;

/**
 * Unit tests for {@link EbxmlEnvelopeParser}
 */
public class EbxmlEnvelopeParserTest {
	private EbxmlEnvelopeParser parser;
	
	@Before
	public void setup() throws Exception {
		parser = new EbxmlEnvelopeParser();
	}
	
	@Test
	public void testParseAck() throws IOException {
		final EbxmlEnvelope expected = getBaseEnvelope();
		expected.setAction("Acknowledgment");
		expected.setAcknowledgment(true);
		expected.getMessageData().setRefToMessageId("5F21C7C0-6CD2-4A50-A879-F3241BA7BE29");
		
		final EbxmlEnvelope actual = parse("./test-ack.xml");
		ReflectionAssert.assertReflectionEquals(expected, actual);
	}
	
	@Test
	public void testParseError() throws IOException {
		final EbxmlEnvelope expected = getBaseEnvelope();
		expected.setAction("MessageError");		
		expected.getMessageData().setRefToMessageId("5F21C7C0-6CD2-4A50-A879-F3241BA7BE29");
		
		expected.addError();
		expected.getError().setListId("1234");
		expected.getError().setId("5678");
		expected.getError().setCode("code");
		expected.getError().setSeverity("Error");
		expected.getError().setCodeContext("context");
		expected.getError().setDescription("Details of the error");
		
		
		final EbxmlEnvelope actual = parse("./test-error.xml");
		ReflectionAssert.assertReflectionEquals(expected, actual);
		assertTrue(expected.isSOAPFault());
	}
	
	@Test
	public void testParseDeliveryFailure() throws IOException {
		final EbxmlEnvelope expected = getBaseEnvelope();
		expected.setAction("Acknowledgment");
		expected.setAcknowledgment(true);
		expected.getMessageData().setRefToMessageId("5F21C7C0-6CD2-4A50-A879-F3241BA7BE29");
		
		expected.addError();
		expected.getError().setDeliveryFailure();
		expected.getError().setError();
		expected.getError().setListId("1234");
		expected.getError().setId("5678");
		expected.getError().setCodeContext("context");
		expected.getError().setDescription("Details of the error");
		
		final EbxmlEnvelope actual = parse("./test-delivery-failure.xml");
		ReflectionAssert.assertReflectionEquals(expected, actual);
		assertFalse(expected.isSOAPFault());
	}
	
	@Test
	public void testParseManifest() throws IOException {
		final EbxmlEnvelope expected = getBaseEnvelope();
		expected.setAction("QWERTY");

		addManifestReference(expected, "cid:3c3f6c55-5cd5-4be8-96db-e918ef188997", true, "HL7 payload");
		addManifestReference(expected, "cid:f7c41ce6-0289-4ddd-bf43-95df37c1dfec", false, "ITK Trunk Message");
		
		final EbxmlEnvelope actual = parse("./test-manifest.xml");
		ReflectionAssert.assertReflectionEquals(expected, actual);
	}
	
	/**
	 * Envelope configured with the expected values common across all tests
	 */
	private EbxmlEnvelope getBaseEnvelope() {
		final EbxmlEnvelope envelope = new EbxmlEnvelope();
		
		envelope.setFromParty("BBB-654321");
		envelope.setToParty("AAA-123456");
		envelope.setCpaId("S3024519A3110234");
		envelope.setConversationId("5F21C7C0-6CD2-4A50-A879-F3241BA7BE29");
		envelope.setService("urn:oasis:names:tc:ebxml-msg:service");
		envelope.getMessageData().setMessageId("F5FECB6E-E891-4381-94AA-E5106A990B04");
		envelope.getMessageData().setTimestamp("2015-06-03T08:27:36");
		
		return envelope;
	}
	
	private ManifestReference addManifestReference(final EbxmlEnvelope envelope, final String href, final boolean hl7,
			final String description) {
		final ManifestReference reference = envelope.addManifestReference();
		reference.setHref(href);
		reference.setHl7(hl7);
		reference.setDescription(description);
		return reference;
	}
	
	private EbxmlEnvelope parse(final String resourceName) throws IOException {
		final InputStream in = getClass().getResourceAsStream(resourceName);
		try {
			return parser.parse(in);
		} finally {
			Closeables.closeQuietly(in);
		}
	}
}
