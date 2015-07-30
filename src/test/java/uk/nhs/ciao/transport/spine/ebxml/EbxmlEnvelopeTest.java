package uk.nhs.ciao.transport.spine.ebxml;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link EbxmlEnvelope}
 */
public class EbxmlEnvelopeTest {
	private EbxmlEnvelope example;
	
	@Before
	public void setup() {
		example = new EbxmlEnvelope();
		example.setFromParty("from");
		example.setToParty("to");
		example.setConversationId("conv");
		example.setCpaId("cpa");
		
		example.getMessageData().setMessageId("123");
	}
	
	@Test
	public void testAckGeneration() {
		final EbxmlEnvelope ack = example.generateAcknowledgment();
		assertReply(ack);
		assertTrue(ack.isAcknowledgment());
		assertFalse(ack.isSOAPFault());
		assertFalse(ack.isManifest());
		
		assertEquals("Acknowledgment", ack.getAction());
	}
	
	@Test
	public void testSOAPFaultGeneration() {
		final EbxmlEnvelope fault = example.generateSOAPFault("code", "some desc");
		assertReply(fault);
		assertTrue(fault.isSOAPFault());
		assertFalse(fault.isAcknowledgment());
		assertFalse(fault.isManifest());
		
		assertEquals("MessageError", fault.getAction());
		
		assertNotNull(fault.getError());
		assertEquals("", fault.getError().getCodeContext());
		assertEquals("code", fault.getError().getCode());
		assertEquals("some desc", fault.getError().getDescription());
	}
	
	/**
	 * Asserts standard field mapping from example -> reply message (e.g. ack, fault, etc)
	 */
	private void assertReply(final EbxmlEnvelope reply) {
		assertEquals("to", reply.getFromParty()); // inverted
		assertEquals("from", reply.getToParty()); // inverted
		assertEquals("conv", reply.getConversationId());
		assertEquals("cpa", reply.getCpaId());
		assertEquals("urn:oasis:names:tc:ebxml-msg:service", reply.getService());
		assertEquals("123", reply.getMessageData().getRefToMessageId());
		assertNotEquals("123", reply.getMessageData().getMessageId()); // ensure the original id was not copied!
		assertNotNull(reply.getMessageData().getTimestamp());
	}
}
