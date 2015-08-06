package uk.nhs.ciao.transport.spine.itk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.xml.bind.DatatypeConverter;

import org.apache.activemq.util.ByteArrayInputStream;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

public class DistributionEnvelope {
	public static final String HANDLING_SPEC_BUSINESS_ACK_REQUESTED = "urn:nhs-itk:ns:201005:ackrequested";
	public static final String HANDLING_SPEC_INFRASTRUCTURE_ACK_REQUESTED = "urn:nhs-itk:ns:201005:infackrequested";
	public static final String HANDLING_SPEC_INTERACTION = "urn:nhs-itk:ns:201005:interaction";
	public static final String INTERACTION_INFRASTRUCTURE_ACK = "urn:nhs-itk:interaction:ITKInfrastructureAcknowledgement-v1-0";
	public static final String INTERACTION_BUSINESS_ACK = "urn:nhs-itk:interaction:ITKBusinessAcknowledgement-v1-0";
	
	private String service;
	private String trackingId;
	private final List<Address> addresses = Lists.newArrayList();
	private Identity auditIdentity;	
	private final List<ManifestItem> manifestItems = Lists.newArrayList();
	private Address senderAddress;
	private final HandlingSpec handlingSpec = new HandlingSpec();
	private final List<Payload> payloads = Lists.newArrayList();
	
	/**
	 * Copies properties from the specified prototype envelope
	 * 
	 * @param prototype The prototype to copy from
	 * @param overwrite true if non-empty properties should be overwritten, or false if the existing values should be kept
	 */
	public void copyFrom(final DistributionEnvelope prototype, final boolean overwrite) {
		if (prototype == null) {
			return;
		}
		
		service = copyProperty(service, prototype.service, overwrite);
		trackingId = copyProperty(trackingId, prototype.trackingId, overwrite);
		auditIdentity = copyProperty(auditIdentity, prototype.auditIdentity, overwrite);
		senderAddress = copyProperty(senderAddress, prototype.senderAddress, overwrite);
		
		handlingSpec.copyFrom(prototype.handlingSpec, overwrite);
		
		if ((addresses.isEmpty() || overwrite) && !prototype.addresses.isEmpty()) {
			addresses.clear();
			
			for (final Address prototypeAddress: prototype.addresses) {
				addAddress(new Address(prototypeAddress));
			}
		}
		
		if ((manifestItems.isEmpty() || overwrite) && !prototype.manifestItems.isEmpty()) {
			manifestItems.clear();
			
			for (final ManifestItem prototypeManifestItem: prototype.manifestItems) {
				addManifestItem(new ManifestItem(prototypeManifestItem));
			}
		}

		if ((payloads.isEmpty() || overwrite) && !prototype.payloads.isEmpty()) {
			payloads.clear();
			
			for (final Payload prototypePayload: prototype.payloads) {
				addPayload(new Payload(prototypePayload));
			}
		}
	}
	
	private String copyProperty(final String original, final String prototype, final boolean overwrite) {
		return (prototype != null && (original == null || overwrite)) ? prototype : original;
	}
	
	private Identity copyProperty(final Identity original, final Identity prototype, final boolean overwrite) {
		return (prototype != null && (original == null || overwrite)) ? new Identity(prototype) : original;
	}
	
	private Address copyProperty(final Address original, final Address prototype, final boolean overwrite) {
		return (prototype != null && (original == null || overwrite)) ? new Address(prototype) : original;
	}
	
	public String getService() {
		return service;
	}
	
	public void setService(final String service) {
		this.service = service;
	}
	
	public String getTrackingId() {
		return trackingId;
	}
	
	public void setTrackingId(final String trackingId) {
		this.trackingId = trackingId;
	}
	
	public List<Address> getAddresses() {
		return addresses;
	}
	
	public void addAddress(final Address address) {
		if (address != null) {
			addresses.add(address);
		}
	}
	
	public Identity getAuditIdentity() {
		return auditIdentity;
	}
	
	public void setAuditIdentity(final String uri) {
		this.auditIdentity = uri == null ? null : new Identity(uri);
	}
	
	public void setAuditIdentity(final Identity auditIdentity) {
		this.auditIdentity = auditIdentity;
	}
	
	public List<ManifestItem> getManifestItems() {
		return manifestItems;
	}
	
	public ManifestItem getManifestItem(final String id) {
		ManifestItem result = null;
		
		for (final ManifestItem manifestItem: manifestItems) {
			if (Objects.equal(id, manifestItem.id)) {
				result = manifestItem;
				break;
			}
		}
		
		return result;
	}
	
	public void addManifestItem(final ManifestItem manifestItem) {
		if (manifestItem != null) {
			manifestItems.add(manifestItem);
		}
	}
	
	public Address getSenderAddress() {
		return senderAddress;
	}
	
	public void setSenderAddress(final String uri) {
		this.senderAddress = uri == null ? null : new Address(uri);
	}
	
	public void setSenderAddress(final Address senderAddress) {
		this.senderAddress = senderAddress;
	}
	
	public HandlingSpec getHandlingSpec() {
		return handlingSpec;
	}
	
	public List<Payload> getPayloads() {
		return payloads;
	}
	
	public Payload getPayload(final String id) {
		Payload result = null;
		
		for (final Payload payload: payloads) {
			if (Objects.equal(id, payload.id)) {
				result = payload;
				break;
			}
		}
		
		return result;
	}
	
	public Payload addPayload(final ManifestItem manifestItem, final String payloadBody) {
		Preconditions.checkNotNull(payloadBody);
		
		if (Strings.isNullOrEmpty(manifestItem.id)) {
			manifestItem.id = generateId();
		}
		
		final Payload payload = new Payload();
		payload.id = manifestItem.id;
		payload.body = payloadBody;
		
		manifestItems.add(manifestItem);
		payloads.add(payload);
		return payload;
	}
	
	public Payload addPayload(final ManifestItem manifestItem, final byte[] payloadBody) throws IOException {
		final String payloadBodyAsString;
		
		if (payloadBody == null) {
			payloadBodyAsString = null;
		} else if (manifestItem.compressed || manifestItem.base64) {
			byte[] bytes = payloadBody;
			if (manifestItem.compressed) {
				ByteArrayOutputStream byteArrayOut = null;
				GZIPOutputStream out = null;
				try {
					byteArrayOut = new ByteArrayOutputStream();
					out = new GZIPOutputStream(byteArrayOut);
					
					out.write(bytes);
					out.flush();
					bytes = byteArrayOut.toByteArray();
				} finally {
					final boolean swallowIOException = true;
					Closeables.close(out, swallowIOException);
					Closeables.close(byteArrayOut, swallowIOException);
				}
			}
			
			if (manifestItem.base64) {
				payloadBodyAsString = DatatypeConverter.printBase64Binary(bytes);
			} else {
				payloadBodyAsString = new String(bytes);
			}
			
		} else {
			payloadBodyAsString = new String(payloadBody);
		}
		
		return addPayload(manifestItem, payloadBodyAsString);
	}
	
	public Payload addPayload(final ManifestItem manifestItem, final String payloadBody, final boolean encodeBody) throws IOException {	
		if (encodeBody && payloadBody != null && (manifestItem.compressed || manifestItem.base64)) {
			return addPayload(manifestItem, payloadBody.getBytes());
		} else {
			return addPayload(manifestItem, payloadBody);
		}
	}
	
	public void addPayload(final Payload payload) {
		if (payload != null) {
			payloads.add(payload);
		}
	}
	
	public byte[] getDecodedPayloadBody(final String id) throws IOException {
		final ManifestItem manifestItem = getManifestItem(id);
		final Payload payload = getPayload(id);

		if (manifestItem == null || payload == null || payload.body == null) {
			return null;
		}
		
		byte[] bytes;
		if (manifestItem.base64) {
			bytes = DatatypeConverter.parseBase64Binary(payload.body);
		} else {
			bytes = payload.body.getBytes();
		}
		
		if (manifestItem.compressed) {
			final GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes));
			try {
				bytes = ByteStreams.toByteArray(in);
			} finally {
				Closeables.closeQuietly(in);
			}
		}
		
		return bytes;
	}
	
	/**
	 * Generates default values for required properties which
	 * have not been specified
	 */
	public void applyDefaults() {
		if (Strings.isNullOrEmpty(trackingId)) {
			trackingId = generateId();
		}
	}
	
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
			.add("service", service)
			.add("trackingId", trackingId)
			.add("addresses", addresses)
			.add("auditIdentity", auditIdentity)
			.add("manifestItems", manifestItems)
			.add("senderAddress", senderAddress)
			.add("handlingSpec", handlingSpec)
			.add("payloads", payloads)
			.toString();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return trackingId == null ? 0 : trackingId.hashCode();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Envelopes are considered equal if they have the same trackingId
	 */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		} else if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		
		final DistributionEnvelope other = (DistributionEnvelope) obj;
		return Objects.equal(trackingId, other.trackingId);
	}
	
	protected String generateId() {
		return UUID.randomUUID().toString();
	}
	
	public static class ManifestItem {
		private String mimeType;
		private String id;
		private String profileId;
		private boolean base64;
		private boolean compressed;
		private boolean encrypted;
		
		public ManifestItem() {
			// Default constructor
		}
		
		/**
		 * Copy constructor
		 */
		public ManifestItem(final ManifestItem copy) {
			mimeType = copy.mimeType;
			id = copy.id;
			profileId = copy.profileId;
			base64 = copy.base64;
			compressed = copy.compressed;
			encrypted = copy.encrypted;
		}

		public String getMimeType() {
			return mimeType;
		}
		
		public void setMimeType(final String mimeType) {
			this.mimeType = mimeType;
		}
		
		public String getId() {
			return id;
		}
		
		public void setId(final String id) {
			this.id = id;
		}
		
		public String getProfileId() {
			return profileId;
		}
		
		public void setProfileId(final String profileId) {
			this.profileId = profileId;
		}
		
		public boolean isBase64() {
			return base64;
		}
		
		public void setBase64(final boolean base64) {
			this.base64 = base64;
		}
		
		public boolean isCompressed() {
			return compressed;
		}
		
		public void setCompressed(final boolean compressed) {
			this.compressed = compressed;
		}
		
		public boolean isEncrypted() {
			return encrypted;
		}
		
		public void setEncrypted(final boolean encrypted) {
			this.encrypted = encrypted;
		}
		
		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this)
				.add("mimeType", mimeType)
				.add("id", id)
				.add("profileId", profileId)
				.add("base64", base64)
				.add("compressed", compressed)
				.add("encrypted", encrypted)
				.toString();
		}
	}
	
	public static class HandlingSpec {
		private Map<String, String> entries = Maps.newLinkedHashMap();
		
		public Map<String, String> getEntries() {
			return entries;
		}
		
		public void copyFrom(final HandlingSpec prototype, boolean overwrite) {
			if (prototype == null) {
				return;
			}
			
			for (final Entry<String, String> prototypeEntry: prototype.entries.entrySet()) {
				if (!entries.containsKey(prototypeEntry.getKey()) || overwrite) {
					entries.put(prototypeEntry.getKey(), prototypeEntry.getValue());
				}
			}
		}

		public void set(final String key, final String value) {
			if (key == null) {
				return;				
			}
			
			if (value == null) {
				entries.remove(key);
			} else {
				entries.put(key, value);
			}
		}
		
		public String get(final String key) {
			return entries.get(key);
		}
		
		public Set<String> getKeys() {
			return entries.keySet();
		}
		
		public String getInteration() {
			return get(HANDLING_SPEC_INTERACTION);
		}
		
		public void setInteration(final String interaction) {
			set(HANDLING_SPEC_INTERACTION, interaction);
		}
		
		public boolean isBusinessAckRequested() {
			return Boolean.parseBoolean(get(HANDLING_SPEC_BUSINESS_ACK_REQUESTED));
		}
		
		public void setBusinessAckRequested(final boolean busAckRequested) {
			set(HANDLING_SPEC_BUSINESS_ACK_REQUESTED, busAckRequested ? "true" : null);
		}
		
		public boolean isBusinessAck() {
			return INTERACTION_BUSINESS_ACK.equalsIgnoreCase(getInteration());
		}
		
		public void setBusinessAck(final boolean busAck) {
			setInteration(busAck ? INTERACTION_BUSINESS_ACK : null);
		}
		
		public boolean isInfrastructureAckRequested() {
			return Boolean.parseBoolean(get(HANDLING_SPEC_INFRASTRUCTURE_ACK_REQUESTED));
		}
		
		public void setInfrastructureAckRequested(final boolean infAckRequested) {
			set(HANDLING_SPEC_INFRASTRUCTURE_ACK_REQUESTED, infAckRequested ? "true" : null);
		}
		
		public boolean isInfrastructureAck() {
			return INTERACTION_INFRASTRUCTURE_ACK.equalsIgnoreCase(getInteration());
		}
		
		public void setInfrastructureAck(final boolean infAck) {
			setInteration(infAck ? INTERACTION_INFRASTRUCTURE_ACK : null);
		}
		
		@Override
		public String toString() {
			return entries.toString();
		}
	}
	
	public static class Address {
		public static final String DEFAULT_TYPE = "2.16.840.1.113883.2.1.3.2.4.18.22";
		private String type;
		private String uri;
		
		public Address() {
			// NOOP
		}
		
		public Address(final String type, final String uri) {
			this.type = type;
			this.uri = uri;
		}
		
		public Address(final String uri) {
			this.uri = uri;
		}
		
		/**
		 * Copy constructor
		 */
		public Address(final Address copy) {
			this.type = copy.type;
			this.uri = copy.uri;
		}
		
		public String getType() {
			return type;
		}
		
		public void setType(final String type) {
			this.type = type;
		}
		
		public String getUri() {
			return uri;
		}
		
		public void setUri(final String uri) {
			this.uri = uri;
		}
		
		public boolean isDefaultType() {
			return Strings.isNullOrEmpty(type) || DEFAULT_TYPE.equals(type);
		}
		
		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this)
				.add("type", type)
				.add("uri", uri)
				.toString();
		}
	}
	
	public static class Payload {
		private String id;
		private String body;
		
		public Payload() {
			// NOOP
		}
		
		/**
		 * Copy constructor
		 */
		public Payload(final Payload payload) {
			id = payload.id;
			body = payload.body;
		}

		public String getId() {
			return id;
		}
		
		public void setId(final String id) {
			this.id = id;
		}
		
		public String getBody() {
			return body;
		}
		
		public void setBody(final String body) {
			this.body = body;
		}
		
		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this)
				.add("id", id)
				.add("body", body)
				.toString();
		}
	}
}
