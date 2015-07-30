package uk.nhs.ciao.transport.spine.itk;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class DistributionEnvelope {
	public static String HANDLING_SPEC_ACK_REQUESTED = "urn:nhs-itk:ns:201005:ackrequested";
	public static String HANDLING_SPEC_INF_ACK_REQUESTED = "urn:nhs-itk:ns:201005:infackrequested";
	public static String HANDLING_SPEC_INTERACTION = "urn:nhs-itk:ns:201005:interaction";
	
	private String service;
	private String trackingId;
	private final List<Address> addresses = Lists.newArrayList();
	private Identity auditIdentity;	
	private final List<ManifestItem> manifestItems = Lists.newArrayList();
	private Address senderAddress;
	private final HandlingSpec handlingSpec = new HandlingSpec();
	private final List<Payload> payloads = Lists.newArrayList();
	
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
		
		public boolean isAckRequested() {
			return Boolean.parseBoolean(get(HANDLING_SPEC_ACK_REQUESTED));
		}
		
		public void setAckRequested(final boolean ackRequested) {
			set(HANDLING_SPEC_ACK_REQUESTED, ackRequested ? "true" : null);
		}
		
		public boolean isInfAckRequested() {
			return Boolean.parseBoolean(get(HANDLING_SPEC_INF_ACK_REQUESTED));
		}
		
		public void setInfAckRequested(final boolean infAckRequested) {
			set(HANDLING_SPEC_INF_ACK_REQUESTED, infAckRequested ? "true" : null);
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
	
	public static class Identity {
		public static final String DEFAULT_TYPE = "2.16.840.1.113883.2.1.3.2.4.18.27";
		private String type;
		private String uri;
		
		public Identity() {
			// NOOP
		}
		
		public Identity(final String type, final String uri) {
			this.type = type;
			this.uri = uri;
		}
		
		public Identity(final String uri) {
			this.uri = uri;
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
