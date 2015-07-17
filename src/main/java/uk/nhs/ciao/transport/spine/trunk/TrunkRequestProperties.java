package uk.nhs.ciao.transport.spine.trunk;

import static com.google.common.base.Preconditions.checkNotNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.Strings;

/**
 * Bean to declare variables required by freemarker
 */
@JsonDeserialize(builder=TrunkRequestProperties.Builder.class)
public class TrunkRequestProperties {	
	private static final String DEFAULT_MIME_BOUNDARY = "--=_MIME-Boundary";
	
	/**
	 * Date format used in ebXml time-stamps
	 * <p>
	 * This is expressed in the UTC time-zone
	 */
	private static final ThreadLocal<DateFormat> EBXML_TIMESTAMP_FORMAT = new ThreadLocal<DateFormat>() {
		protected DateFormat initialValue() {
			final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
			return dateFormat;
		};
	};
	
	/**
	 * Date format used in HL7 date-time fields
	 * <p>
	 * This is expressed in the local time-zone
	 */
	private static final ThreadLocal<DateFormat> HL7_DATE_FORMAT = new ThreadLocal<DateFormat>() {
		protected DateFormat initialValue() {
			return new SimpleDateFormat("yyyyMMddHHmmss");
		};
	};

	
	/**
	 * Uses:
	 * HTTP Content-Type header - boundary parameter
	 * Splits individual Multipart MIME sections in request body
	 * 
	 * A static value can be chosen - but it should be a value
	 * with a small chance of appearing in actual multi-part
	 * content - otherwise the document may fail to parse
	 * properly
	 */
	private final String mimeBoundary;
	
	// ebxml variables
	
	/**
	 * Uses:
	 * HTTP Content-Type header - start parameter
	 * Multipart inline header - Content-ID for ebxml part
	 */
	private final String ebxmlContentId;
	
	/**
	 * Uses (Request):
	 * eb:ConversationId
	 * eb:MessageId
	 * 
	 * Uses (ebxml ACK):
	 * eb:ConversationId
	 * eb:RefToMessageId
	 * (ACK itself has it's own eb:MessageId)
	 * <p>
	 * This might be better split into two variables conversationId and messageId - for
	 * the initial request they can have the same value (for convenience). The subsequent
	 * ack will refer to conversationId but will have it's own messageId value.
	 * <p>
	 * The ack (probably) ends the conversation from the ebXml perspective. Subsequent
	 * ITK ack messages 'may' be considered new conversations (from the ebXml perspective).
	 * They will contain references to the original ITK trackingId - but may not have the
	 * same ebXml conversationId. It might be safer not to rely on the conversationID being
	 * the same across the whole (ITK) interaction - in any case when replying to the ITK
	 * acks (with ebXml acks) the incoming ebXml values should be echoed back in the ebXml
	 * ack. At a minimum, this requires storing the incoming eb:MessageId in persistent
	 * storage to support duplicate detection (as described in the ebXml spec).
	 */
	private final String ebxmlCorrelationId;
	
	/**
	 * Uses:
	 * eb:Timestamp (in MessageData)
	 * HL7 - creationTime
	 * 
	 * String format methods exist for ebxml and hl7 (different formats
	 * and different time-zones)
	 * 
	 * @see #getEbxmlTimestamp()
	 * @see #getHl7CreationTime()
	 */
	private final Date creationTime;
	
	// HL7 variables
	
	/**
	 * Uses:
	 * Multipart inline header - Content-ID for HL7 part
	 * Manifest reference in ebxml part
	 */
	private final String hl7ContentId;
	
	/**
	 * Uses:
	 * HL7 root ID - no other references
	 */
	private final String hl7RootId;
	
	// ITK variables
	
	/**
	 * Uses:
	 * Multipart inline header - Content-ID for ITK part
	 * Manifest reference in ebxml part
	 */
	private final String itkContentId;
	
	/**
	 * Uses (ITK Request):
	 * trackingId
	 * 
	 * Uses (ITK Inf ack):
	 * trackingIdRef
	 * 
	 * Uses (ITK Bus Ack):
	 * hl7:conveyingTransmission -> id
	 * 
	 * This ID is used throughout the ITK message interactions and spans
	 * multiple ebXml interactions (the ebXml conversationId 'may' change).
	 * <p>
	 * Presumably the same trackingId should be used when resending messages
	 * at the ITK protocol level (e.g. if an ebXml ack was received but
	 * no ITK acks were received). In this case a new ebXml messageId would
	 * be used - possibly a new ebXml conversationId too.
	 * <p>
	 * For the time being however, resends at the ITK protocol level are out of scope.
	 */
	private final String itkCorrelationId;
	
	/**
	 * Uses (ITK request)
	 * manifestitem id
	 * payload id
	 * Also - internal ID from ClinicalDocument but without the uuid_ prefix
	 * 
	 * Uses (ITK bus ack):
	 * conveyingTransmition -> id
	 * (This refers to the payload id (with uuid_ prefix) - not the internal ClinicalDocument id)
	 * 
	 * TODO: Are these IDs related as a convenience or is it required by the protocol
	 */
	private final String itkDocumentId;
	
	private final String senderPartyId;
	private final String senderAsid;
	private final String senderODSCode;
	
	private final String receiverPartyId;
	private final String receiverAsid;
	private final String receiverODSCode;
	private final String receiverCPAId;
	
	private final String auditODSCode;
	
	private final String interactionId;
	private final String itkProfileId;
	private final String itkHandlingSpec;
	
	/**
	 * Document payload
	 * <p>
	 * If document type is NOT xml additional encoding may be required to 
	 * escape reserved XML characters
	 * 
	 * TODO: Check documentation for further details on GZIP encoding
	 */
	private final String itkDocumentBody;

	
	// Using builder pattern to only generate UUIDs when actually required
	private TrunkRequestProperties(final Builder builder) {
		this.mimeBoundary = valueOrDefault(builder.properties.mimeBoundary, DEFAULT_MIME_BOUNDARY);
		this.ebxmlContentId = valueOrUUID(builder.properties.ebxmlContentId);
		this.ebxmlCorrelationId = valueOrUUID(builder.properties.ebxmlCorrelationId);
		this.creationTime = valueOrNow(builder.properties.creationTime);
		this.hl7ContentId = valueOrUUID(builder.properties.hl7ContentId);
		this.hl7RootId = valueOrUUID(builder.properties.hl7RootId);
		this.itkContentId = valueOrUUID(builder.properties.itkContentId);
		this.itkCorrelationId = valueOrUUID(builder.properties.itkCorrelationId);
		this.itkDocumentId = valueOrUUID(builder.properties.itkDocumentId);
		
		this.senderPartyId = checkNotNull(builder.properties.senderPartyId);
		this.senderAsid = checkNotNull(builder.properties.senderAsid);
		this.senderODSCode = checkNotNull(builder.properties.senderODSCode);
		
		this.receiverPartyId = checkNotNull(builder.properties.receiverPartyId);
		this.receiverAsid = checkNotNull(builder.properties.receiverAsid);
		this.receiverODSCode = checkNotNull(builder.properties.receiverODSCode);
		this.receiverCPAId = checkNotNull(builder.properties.receiverCPAId);
		
		this.auditODSCode = valueOrDefault(builder.properties.auditODSCode, builder.properties.senderODSCode);
		
		this.interactionId = checkNotNull(builder.properties.interactionId);
		this.itkProfileId = checkNotNull(builder.properties.itkProfileId);
		this.itkHandlingSpec = checkNotNull(builder.properties.itkHandlingSpec);
		
		checkNotNull(builder.originalDocument.itkDocumentBody);
		// TODO: compress and encode as Base64
		this.itkDocumentBody = new String(builder.originalDocument.itkDocumentBody);
	}

	public String getMimeBoundary() {
		return mimeBoundary;
	}

	public String getEbxmlContentId() {
		return ebxmlContentId;
	}

	public String getEbxmlCorrelationId() {
		return ebxmlCorrelationId;
	}
	
	public String getEbxmlTimestamp() {
		return EBXML_TIMESTAMP_FORMAT.get().format(creationTime);
	}

	public Date getCreationTime() {
		return creationTime;
	}

	public String getHl7ContentId() {
		return hl7ContentId;
	}

	public String getHl7RootId() {
		return hl7RootId;
	}
	
	public String getHl7CreationTime() {
		return HL7_DATE_FORMAT.get().format(creationTime);
	}

	public String getItkContentId() {
		return itkContentId;
	}

	public String getItkCorrelationId() {
		return itkCorrelationId;
	}

	public String getItkDocumentId() {
		return itkDocumentId;
	}

	public String getItkDocumentBody() {
		return itkDocumentBody;
	}
	
	public String getSenderPartyId() {
		return senderPartyId;
	}

	public String getSenderAsid() {
		return senderAsid;
	}

	public String getSenderODSCode() {
		return senderODSCode;
	}

	public String getReceiverPartyId() {
		return receiverPartyId;
	}

	public String getReceiverAsid() {
		return receiverAsid;
	}

	public String getReceiverODSCode() {
		return receiverODSCode;
	}

	public String getReceiverCPAId() {
		return receiverCPAId;
	}

	public String getAuditODSCode() {
		return auditODSCode;
	}

	public String getInteractionId() {
		return interactionId;
	}

	public String getItkProfileId() {
		return itkProfileId;
	}

	public String getItkHandlingSpec() {
		return itkHandlingSpec;
	}

	public static Builder builder() {
		return new Builder();
	}
	
	/**
	 * Builds {@link TrunkRequestProperties} instances
	 */
	@JsonPOJOBuilder
	@JsonIgnoreProperties(ignoreUnknown=true)
	public static class Builder {
		@JsonProperty
		private PropertiesView properties = new PropertiesView();
		
		@JsonProperty
		private OriginalDocumentView originalDocument = new OriginalDocumentView();
		
		// For jackson
		void setProperties(final PropertiesView properties) {
			this.properties = checkNotNull(properties);
		}
		
		// For jackson
		void setOriginalDocument(final OriginalDocumentView originalDocument) {
			this.originalDocument = checkNotNull(originalDocument);
		}
		
		public Builder setMimeBoundary(final String mimeBoundary) {
			properties.mimeBoundary = mimeBoundary;
			return this;
		}
		
		public Builder setEbxmlContentId(final String ebxmlContentId) {
			properties.ebxmlContentId = ebxmlContentId;
			return this;
		}
		
		public Builder setEbxmlCorrelationId(final String ebxmlCorrelationId) {
			properties.ebxmlCorrelationId = ebxmlCorrelationId;
			return this;
		}
		
		public Builder setCreationTime(final Date creationTime) {
			properties.creationTime = creationTime;
			return this;
		}
		
		public Builder setHl7ContentId(final String hl7ContentId) {
			properties.hl7ContentId = hl7ContentId;
			return this;
		}
		
		public Builder setHl7RootId(final String hl7RootId) {
			properties.hl7RootId = hl7RootId;
			return this;
		}
		
		public Builder setItkContentId(final String itkContentId) {
			properties.itkContentId = itkContentId;
			return this;
		}
		
		public Builder setItkCorrelationId(final String itkCorrelationId) {
			properties.itkCorrelationId = itkCorrelationId;
			return this;
		}
		
		public Builder setItkDocumentId(final String itkDocumentId) {
			properties.itkDocumentId = itkDocumentId;
			return this;
		}
		
		public Builder setItkDocumentBody(final byte[] itkDocumentBody) {
			originalDocument.itkDocumentBody = itkDocumentBody;
			return this;
		}
		
		public Builder setSenderPartyId(final String senderPartyId) {
			properties.senderPartyId = senderPartyId;
			return this;
		}
		
		public Builder setSenderAsid(final String senderAsid) {
			properties.senderAsid = senderAsid;
			return this;
		}
		
		public Builder setSenderODSCode(final String senderODSCode) {
			properties.senderODSCode = senderODSCode;
			return this;
		}
		
		public Builder setReceiverPartyId(final String receiverPartyId) {
			properties.receiverPartyId = receiverPartyId;
			return this;
		}
		
		public Builder setReceiverAsid(final String receiverAsid) {
			properties.receiverAsid = receiverAsid;
			return this;
		}
		
		public Builder setReceiverODSCode(final String receiverODSCode) {
			properties.receiverODSCode = receiverODSCode;
			return this;
		}
		
		public Builder setReceiverCPAId(final String receiverCPAId) {
			properties.receiverCPAId = receiverCPAId;
			return this;
		}
		
		public Builder setAuditODSCode(final String auditODSCode) {
			properties.auditODSCode = auditODSCode;
			return this;
		}
		
		public Builder setInteractionId(final String interactionId) {
			properties.interactionId = interactionId;
			return this;
		}
		
		public Builder setItkProfileId(final String itkProfileId) {
			properties.itkProfileId = itkProfileId;
			return this;
		}
		
		public Builder setItkHandlingSpec(final String itkHandlingSpec) {
			properties.itkHandlingSpec = itkHandlingSpec;
			return this;
		}
		
		public TrunkRequestProperties build() {
			return new TrunkRequestProperties(this);
		}
	}
	
	// The rather 'odd' class structure is to coerce the JSON hierarchy
	// of ParsedDocument into the flat view used by freemarker
	// Jackson can't yet handle this via annotations
	// see https://github.com/FasterXML/jackson-annotations/issues/42
	
	@JsonIgnoreProperties(ignoreUnknown=true)
	static class PropertiesView {
		@JsonProperty
		private String mimeBoundary;
		@JsonProperty
		private String ebxmlContentId;
		@JsonProperty
		private String ebxmlCorrelationId;
		@JsonProperty
		private Date creationTime;
		@JsonProperty
		private String hl7ContentId;
		@JsonProperty
		private String hl7RootId;
		@JsonProperty
		private String itkContentId;
		@JsonProperty
		private String itkCorrelationId;
		@JsonProperty
		private String itkDocumentId;
		
		@JsonProperty
		private String senderPartyId;
		@JsonProperty
		private String senderAsid;
		@JsonProperty
		private String senderODSCode;
		
		@JsonProperty
		private String receiverPartyId;
		@JsonProperty
		private String receiverAsid;
		@JsonProperty
		private String receiverODSCode;
		@JsonProperty
		private String receiverCPAId;
		
		@JsonProperty
		private String auditODSCode;
		
		@JsonProperty
		private String interactionId;
		@JsonProperty
		private String itkProfileId;
		@JsonProperty
		private String itkHandlingSpec;
	}
	
	@JsonIgnoreProperties(ignoreUnknown=true)
	static class OriginalDocumentView {
		@JsonProperty(value="content")
		private byte[] itkDocumentBody;
	}
	
	private static String valueOrDefault(final String value, final String defaultValue) {
		return Strings.isNullOrEmpty(value) ? defaultValue : value;
	}
	
	private static String valueOrUUID(final String value) {
		return Strings.isNullOrEmpty(value) ? UUID.randomUUID().toString() : value;
	}
	
	private static Date valueOrNow(final Date value) {
		return value == null ? new Date() : value;
	}
}