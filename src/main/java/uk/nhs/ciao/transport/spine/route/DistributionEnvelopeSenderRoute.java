package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.Exchange;
import org.apache.camel.Property;

import com.google.common.base.Strings;

import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope;
import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope.ManifestReference;
import uk.nhs.ciao.transport.spine.hl7.HL7Part;
import uk.nhs.ciao.transport.spine.itk.Address;
import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope;
import uk.nhs.ciao.transport.spine.multipart.MultipartBody;
import uk.nhs.ciao.transport.spine.multipart.Part;
import uk.nhs.ciao.transport.spine.sds.SpineEndpointAddress;

/**
 * Route to send an ITK distribution envelope over spine
 * <p>
 * Default sender properties are added to the envelope (if required) before
 * the distribution envelope is prepared for sending over spine (by creating
 * a multi-part wrapper) and storing it on a queue for sending by
 * the multi-part message sender route.
 */
public class DistributionEnvelopeSenderRoute extends BaseRouteBuilder {
	private String distributionEnvelopeSenderUri;
	private String multipartMessageSenderUri;
	private String spineEndpointAddressEnricherUrl;
	
	// optional properties
	private DistributionEnvelope prototypeDistributionEnvelope;
	private EbxmlEnvelope prototypeEbxmlManifest;
	private HL7Part prototypeHl7Part;
	
	/**
	 * URI where incoming distribution envelope messages are received from
	 * <p>
	 * input only
	 */
	public void setDistributionEnvelopeSenderUri(final String distributionEnvelopeSenderUri) {
		this.distributionEnvelopeSenderUri = distributionEnvelopeSenderUri;
	}
	
	/**
	 * URI where outgoing multi-part messages are sent to
	 * <p>
	 * output only
	 */
	public void setMultipartMessageSenderUri(final String multipartMessageSenderUri) {
		this.multipartMessageSenderUri = multipartMessageSenderUri;
	}
	
	/**
	 * URI of service used to enrich destination endpoint address details
	 */
	public void setSpineEndpointAddressEnricherUri(final String spineEndpointAddressEnricherUrl) {
		this.spineEndpointAddressEnricherUrl = spineEndpointAddressEnricherUrl;
	}
	
	/**
	 * Sets the prototype distribution envelope containing default properties that should
	 * be added to all envelopes before they are sent.
	 * <p>
	 * Non-empty properties on the envelope being sent are not overwritten.
	 * 
	 * @param prototype The prototype envelope
	 */
	public void setPrototypeDistributionEnvelope(final DistributionEnvelope prototype) {
		prototypeDistributionEnvelope = prototype;
	}
	
	/**
	 * Sets the prototype ebXml manifest containing default properties that should
	 * be added to all manifests before they are sent.
	 * <p>
	 * Non-empty properties on the manifest being sent are not overwritten.
	 * 
	 * @param prototype The prototype manifest
	 */
	public void setPrototypeEbxmlManifest(final EbxmlEnvelope prototype) {
		prototypeEbxmlManifest = prototype;
	}
	
	/**
	 * Sets the prototype HL7 part containing default properties that should
	 * be added to all parts before they are sent.
	 * <p>
	 * Non-empty properties on the part being sent are not overwritten.
	 * 
	 * @param prototype The prototype part
	 */
	public void setPrototypeHl7Part(final HL7Part prototype) {
		prototypeHl7Part = prototype;
	}
	
	@Override
	public void configure() throws Exception {
		/*
		 * The output will be a multipart body - individual parts are constructed
		 * and stored as properties until the body is built in the final stage 
		 */
		from(distributionEnvelopeSenderUri)				
			// Configure the distribution envelope
			.convertBodyTo(DistributionEnvelope.class)
			.bean(new DistributionEnvelopePopulator())
			.setProperty("distributionEnvelope").body()
			
			// Resolve destination address
			.bean(new DestinationAddressBuilder())
			.to(spineEndpointAddressEnricherUrl)
			.setProperty("destination").body()

			// Configure ebxml manifest
			.bean(new EbxmlManifestBuilder())
			.setHeader(Exchange.CORRELATION_ID).simple("${body.messageData.messageId}")
			.setHeader("SOAPAction").simple("${body.service}/${body.action}")
			.setProperty("ebxmlManifest").body()
			
			// add hl7 part
			.bean(new HL7PartBuilder())
			.setProperty("hl7Part").body()
			
			// Build the multi-part request
			.bean(new MultipartBodyBuilder())
			.setHeader(Exchange.CONTENT_TYPE).simple("multipart/related; boundary=\"${body.boundary}\"; type=\"text/xml\"; start=\"${body.parts[0].rawContentId}\"")
			.convertBodyTo(String.class)
			
			.to(multipartMessageSenderUri)
		.end();
	}
	
	// Processor / bean methods
	// The methods can't live in the route builder - it causes havoc with the debug/tracer logging
	
	/**
	 * Populates the distribution envelope with default properties if they have
	 * not already been specified in the envelope.
	 */
	public class DistributionEnvelopePopulator {
		public void populateDistributionEnvelope(final DistributionEnvelope envelope) {
			final boolean overwrite = false;
			envelope.copyFrom(prototypeDistributionEnvelope, overwrite);
			envelope.applyDefaults();
		}
	}
	
	public class DestinationAddressBuilder {
		public SpineEndpointAddress buildDestinationAdddress(final DistributionEnvelope envelope) {
			final SpineEndpointAddress destination = new SpineEndpointAddress();
			
			final Address address = envelope.getAddresses().get(0);
			if (address.isASID()) {
				destination.setAsid(address.getUri());
			} else if (address.isODS()) {
				destination.setOdsCode(address.getODSCode());
			}
			
			if (prototypeEbxmlManifest != null) {
				destination.setService(prototypeEbxmlManifest.getService());
				destination.setAction(prototypeEbxmlManifest.getAction());
			}
			
			return destination;
		}
	}
	
	/**
	 * Builds an EbxmlManifest for the specified DistributionEnvelope
	 */
	public class EbxmlManifestBuilder {
		public EbxmlEnvelope startEbxmlManifest(@Property("distributionEnvelope") final DistributionEnvelope envelope,
				@Property("destination") final SpineEndpointAddress destination) {
			final EbxmlEnvelope ebxmlManifest = new EbxmlEnvelope();
			ebxmlManifest.setAckRequested(true);
			
			if (!Strings.isNullOrEmpty(destination.getService())) {
				ebxmlManifest.setService(destination.getService());
			}
			
			if (!Strings.isNullOrEmpty(destination.getAction())) {
				ebxmlManifest.setAction(destination.getAction());
			}
			
			if (!Strings.isNullOrEmpty(destination.getCpaId())) {
				ebxmlManifest.setCpaId(destination.getCpaId());
			}
			
			if (!Strings.isNullOrEmpty(destination.getMhsPartyKey())) {
				ebxmlManifest.setToParty(destination.getMhsPartyKey());
			}
			
			// Apply defaults from the prototype
			final boolean overwrite = false;
			ebxmlManifest.copyFrom(prototypeEbxmlManifest, overwrite);
			ebxmlManifest.applyDefaults();
			
			return ebxmlManifest;
		}
	}
	
	/**
	 * Builds an EbxmlManifest for the specified DistributionEnvelope
	 */
	public class HL7PartBuilder {
		public HL7Part buildHl7Part(@Property("ebxmlManifest") final EbxmlEnvelope ebxmlManifest,
				@Property("distributionEnvelope") final DistributionEnvelope envelope,
				@Property("destination") final SpineEndpointAddress destination) {
			final HL7Part hl7Part = new HL7Part();
			
			if (!Strings.isNullOrEmpty(destination.getAsid())) {
				hl7Part.setReceiverAsid(destination.getAsid());
			}
			hl7Part.setInteractionId(ebxmlManifest.getAction());
			
			// Apply defaults from the prototype
			final boolean overwrite = false;
			hl7Part.copyFrom(prototypeHl7Part, overwrite);
			hl7Part.applyDefaults();
			
			return hl7Part;
		}
	}
	
	/**
	 * Creates a new MultipartBody instance with the specified parts
	 */
	public class MultipartBodyBuilder {
		public MultipartBody createMultipartBody(@Property("ebxmlManifest") final EbxmlEnvelope ebxmlManifest,
				@Property("hl7Part") final String hl7Payload,
				@Property("distributionEnvelope") final String itkPayload) throws Exception {
			final MultipartBody body = new MultipartBody();
			final Part ebxmlPart = body.addPart("text/xml", ebxmlManifest);
			final String hl7Id = body.addPart("application/xml; charset=UTF-8", hl7Payload).getContentId();
			final String itkId = body.addPart("text/xml", itkPayload).getContentId();
			
			// The manifest can now be completed		
			final ManifestReference hl7Reference = ebxmlManifest.addManifestReference();
			hl7Reference.setHref("cid:" + hl7Id);
			hl7Reference.setDescription("HL7 payload");
			hl7Reference.setHl7(true);
			
			final ManifestReference itkReference = ebxmlManifest.addManifestReference();
			itkReference.setHref("cid:" + itkId);
			itkReference.setDescription("ITK payload");
			
			// Lastly - encode and store the updated manifest (as an XML body)
			ebxmlPart.setBody(getContext().getTypeConverter().mandatoryConvertTo(String.class, ebxmlManifest));
			
			return body;
		}
	}
}
