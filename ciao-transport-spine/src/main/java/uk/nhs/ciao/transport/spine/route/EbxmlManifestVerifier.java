package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope;
import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope.ManifestReference;
import uk.nhs.ciao.transport.spine.multipart.MultipartBody;
import uk.nhs.ciao.transport.spine.multipart.Part;

/**
 * Processor which verifies that a specified multipart body contains an ebxml manifest, and 
 * that the manifest properly describes the remaining parts of the multipart body.
 * <p>
 * The extracted manifest is stored as a property on the exchange called {@link #MANIFEST_PROPERTY}.
 */
public class EbxmlManifestVerifier implements Processor {
	/**
	 * The property used to store the extracted manifest on the {@link Exchange}
	 */
	public static final String MANIFEST_PROPERTY = "multipart-manifest";
	
	@Override
	public void process(final Exchange exchange) throws Exception {
		final MultipartBody multipartBody = exchange.getIn().getMandatoryBody(MultipartBody.class);
		final EbxmlEnvelope manifest = getManifest(multipartBody);
		storeManifest(exchange, manifest);
		
		final EbxmlEnvelope soapFault = verifyManifest(manifest, multipartBody);			
		if (soapFault != null) {
			final Message message = exchange.getIn();
			message.setBody(soapFault, String.class);
			message.setFault(true);
			message.setHeader(Exchange.HTTP_RESPONSE_CODE, "500");
			
			if (exchange.getPattern().isOutCapable()) {
                exchange.setOut(message);
            }
		}
	}
	
	private EbxmlEnvelope getManifest(final MultipartBody multipartBody) throws Exception {
		final Part ebxmlPart = multipartBody.getParts().get(0);
		return ebxmlPart.getMandatoryBody(EbxmlEnvelope.class);
	}
	
	private void storeManifest(final Exchange exchange, final EbxmlEnvelope manifest) {
		exchange.setProperty(MANIFEST_PROPERTY, manifest);
	}
	
	private EbxmlEnvelope verifyManifest(final EbxmlEnvelope manifest, final MultipartBody multipartBody) throws Exception {
		for (final ManifestReference reference: manifest.getManifestReferences()) {
			final String href = reference.getHref();
			if (!href.toLowerCase().startsWith("cid:")) {
				return clientFault(manifest, "Invalid href reference: " + href);
			}
			
			final String contentId = href.substring(4);
			final Part referencedPart =  multipartBody.findPartByContentId(contentId);
			if (referencedPart == null) {
				return clientFault(manifest, "Part referenced in manifest could not be found: " + reference);
			}
		}
		
		return null; // no fault
	}
	
	private EbxmlEnvelope clientFault(final EbxmlEnvelope manifest, final String description) {
		return manifest.generateSOAPFault(EbxmlEnvelope.ERROR_CODE_CLIENT, description);
	}
}