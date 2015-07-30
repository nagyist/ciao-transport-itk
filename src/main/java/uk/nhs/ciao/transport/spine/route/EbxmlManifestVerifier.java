package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope;
import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope.ManifestReference;
import uk.nhs.ciao.transport.spine.multipart.MultipartBody;
import uk.nhs.ciao.transport.spine.multipart.Part;

public class EbxmlManifestVerifier implements Processor {
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
	
	public EbxmlEnvelope getManifest(final MultipartBody multipartBody) throws Exception {
		final Part ebxmlPart = multipartBody.getParts().get(0);
		return ebxmlPart.getMandatoryBody(EbxmlEnvelope.class);
	}
	
	public void storeManifest(final Exchange exchange, final EbxmlEnvelope manifest) {
		exchange.setProperty("ebxmlManifest", manifest);
	}
	
	public EbxmlEnvelope verifyManifest(final EbxmlEnvelope manifest, final MultipartBody multipartBody) throws Exception {
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