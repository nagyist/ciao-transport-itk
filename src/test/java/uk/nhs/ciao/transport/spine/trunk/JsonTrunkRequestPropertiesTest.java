package uk.nhs.ciao.transport.spine.trunk;

import java.io.IOException;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;

import uk.nhs.ciao.docs.parser.Document;
import uk.nhs.ciao.docs.parser.ParsedDocument;

/**
 * Tests the conversion of a JSON encoded ParsedDocument into a {@link TrunkRequestProperties}
 * using Jackson
 */
public class JsonTrunkRequestPropertiesTest {
	private ObjectMapper objectMapper;
	
	@Before
	public void setup() {
		objectMapper = new ObjectMapper();
	}
	
	@Test
	public void testJsonToTrunkRequestProperties() throws IOException {
		final ParsedDocument parsedDocument = createParsedDocument();
		final String json = objectMapper.writeValueAsString(parsedDocument);
		
		final TrunkRequestProperties trunkRequestProperties = objectMapper.readValue(json, TrunkRequestProperties.class);
		
		Assert.assertNotNull(trunkRequestProperties);
		Assert.assertEquals("document content", trunkRequestProperties.getItkDocumentBody());
		Assert.assertEquals("12345", trunkRequestProperties.getItkCorrelationId());
	}
	
	private ParsedDocument createParsedDocument() {
		final Map<String, Object> properties = Maps.newLinkedHashMap();
		properties.put("testkey", "testvalue");
		properties.put("itkCorrelationId", "12345");
		properties.put("senderPartyId", "AAA-123456");
		properties.put("senderAsid", "866971180017");
		properties.put("senderODSCode", "AAA");
		properties.put("receiverPartyId", "BBB-654321");
		properties.put("receiverAsid", "000000000000");
		properties.put("receiverODSCode", "BBB");
		properties.put("receiverCPAId", "S3024519A3110234");
		properties.put("auditODSCode", "AAA");
		properties.put("interactionId", "COPC_IN000001GB01");
		properties.put("itkProfileId", "urn:nhs-en:profile:eDischargeInpatientDischargeSummary-v1-0");
		properties.put("itkHandlingSpec", "urn:nhs-itk:interaction:copyRecipientAmbulanceServicePatientReport-v1-0");

		final Document originalDocument = new Document("filename.txt", "document content".getBytes(), "text/plain");
		
		return new ParsedDocument(originalDocument, properties);
	}
}
