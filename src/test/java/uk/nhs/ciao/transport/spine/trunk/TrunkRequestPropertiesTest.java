package uk.nhs.ciao.transport.spine.trunk;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Maps;

import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.configuration.impl.MemoryCipProperties;
import uk.nhs.ciao.docs.parser.Document;
import uk.nhs.ciao.docs.parser.ParsedDocument;

/**
 * Tests the conversion of a ParsedDocument/CIAOConfig pair into a {@link TrunkRequestProperties}
 * using Jackson
 */
public class TrunkRequestPropertiesTest {
	@Test
	public void testParsedDocumentToTrunkRequestProperties() throws Exception {
		final CIAOConfig config = createCIAOConfig();
		final ParsedDocument parsedDocument = createParsedDocument();
		
		final TrunkRequestProperties trunkRequestProperties = new TrunkRequestProperties(config, parsedDocument);
		
		Assert.assertNotNull(trunkRequestProperties);
		
		// Check properties from parsed document
		Assert.assertEquals("document content", trunkRequestProperties.getItkDocumentBody());
		Assert.assertEquals("12345", trunkRequestProperties.getItkCorrelationId());
		
		/// Check properties from CIAOConfig
		Assert.assertEquals("AAA", trunkRequestProperties.getAuditODSCode());
		
		// Check overridden properties (CIAOConfig -> parsed document)
		Assert.assertEquals("CCC", trunkRequestProperties.getSenderODSCode());
	}
	
	private CIAOConfig createCIAOConfig() {
		final Map<String, String> configValues = Maps.newLinkedHashMap();
		
		configValues.put("senderPartyId", "AAA-123456");
		configValues.put("senderAsid", "866971180017");
		configValues.put("senderODSCode", "AAA");
		configValues.put("auditODSCode", "AAA");
		configValues.put("interactionId", "COPC_IN000001GB01");
		
		return new CIAOConfig(new MemoryCipProperties("cip-name", "cip-version", configValues));
	}

	private ParsedDocument createParsedDocument() {
		final Map<String, Object> properties = Maps.newLinkedHashMap();
		properties.put("testkey", "testvalue");
		properties.put("itkCorrelationId", "12345");
		properties.put("senderOdsCode", "CCC"); // overridden (and checking case insensitive keys)
		properties.put("receiverPartyId", "BBB-654321");
		properties.put("receiverAsid", "000000000000");
		properties.put("receiverODSCode", "BBB");
		properties.put("receiverCPAId", "S3024519A3110234");
		properties.put("itkProfileId", "urn:nhs-en:profile:eDischargeInpatientDischargeSummary-v1-0");
		properties.put("itkHandlingSpec", "urn:nhs-itk:interaction:copyRecipientAmbulanceServicePatientReport-v1-0");

		final Document originalDocument = new Document("filename.txt", "document content".getBytes(), "text/plain");
		
		return new ParsedDocument(originalDocument, properties);
	}
}
