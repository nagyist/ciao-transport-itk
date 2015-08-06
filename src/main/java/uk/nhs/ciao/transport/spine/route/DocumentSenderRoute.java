package uk.nhs.ciao.transport.spine.route;

import java.util.Map;

import org.apache.camel.model.dataformat.JsonLibrary;

import uk.nhs.ciao.docs.parser.Document;
import uk.nhs.ciao.docs.parser.ParsedDocument;
import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope;
import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope.Address;
import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope.ManifestItem;

/**
 * Route to send an incoming document
 * <p>
 * An ITK distribution envelope is constructed an send to the configured
 * <code>distributionEnvelopeSenderUri</code>.
 */
public class DocumentSenderRoute extends BaseRouteBuilder {
	private String documentSenderRouteUri;
	private String distributionEnvelopeSenderUri;

	public void setDocumentSenderRouteUri(final String documentSenderRouteUri) {
		this.documentSenderRouteUri = documentSenderRouteUri;
	}
	
	public void setDistributionEnvelopeSenderUri(final String distributionEnvelopeSenderUri) {
		this.distributionEnvelopeSenderUri = distributionEnvelopeSenderUri;
	}
	
	@Override
	public void configure() throws Exception {
		from(documentSenderRouteUri)
			.unmarshal().json(JsonLibrary.Jackson, ParsedDocument.class)
			.bean(this, "buildDistributionEnvelope")
			.to(distributionEnvelopeSenderUri)
		.end();
	}
	
	public DistributionEnvelope buildDistributionEnvelope(final ParsedDocument parsedDocument) throws Exception {
		final Document document = parsedDocument.getOriginalDocument();
		final Map<String, Object> properties = parsedDocument.getProperties();
		
		if (document.isEmpty()) {
			throw new Exception("No document to send");
		}
		
		final DistributionEnvelope envelope = new DistributionEnvelope();
		envelope.getHandlingSpec().setInfrastructureAckRequested(true);
		envelope.getHandlingSpec().setBusinessAckRequested(true);
		
		if (properties.containsKey("receiverODSCode")) {
			envelope.addAddress(new Address("urn:nhs-uk:addressing:ods:" + properties.get("receiverODSCode")));
		}
		
		if (properties.containsKey("senderODSCode")) {
			envelope.setSenderAddress("urn:nhs-uk:addressing:ods:" + properties.get("senderODSCode"));
		}
		
		if (properties.containsKey("auditODSCode")) {
			envelope.setAuditIdentity("urn:nhs-uk:addressing:ods:" + properties.get("senderODSCode"));
		}
		
		if (properties.containsKey("itkHandlingSpec")) {
			envelope.getHandlingSpec().setInteration(String.valueOf(properties.get("itkHandlingSpec")));
		}
		
		final ManifestItem manifestItem = new ManifestItem();
		manifestItem.setMimeType(document.getMediaType());
		if (document.getMediaType() == null || !document.getMediaType().contains("xml")) {
			// non-xml content cannot be added ti the envelope without encoding
			manifestItem.setCompressed(true);
			manifestItem.setBase64(true);
		}
		
		envelope.addPayload(manifestItem, document.getContent());
		
		envelope.applyDefaults();
		
		return envelope;
	}
}
