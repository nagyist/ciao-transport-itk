package uk.nhs.ciao.transport.spine.trunk;

import java.util.Date;
import java.util.UUID;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * Bean to declare variables required by freemarker
 */
public class TrunkRequestProperties {	
	private static final String DEFAULT_MIME_BOUNDARY = "--=_MIME-Boundary";
	
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
	 * TODO: Should this change if message is resent? Do they always refer to this same time?
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
		this.mimeBoundary = valueOrDefault(builder.mimeBoundary, DEFAULT_MIME_BOUNDARY);
		this.ebxmlContentId = valueOrUUID(builder.ebxmlContentId);
		this.ebxmlCorrelationId = valueOrUUID(builder.ebxmlCorrelationId);
		this.creationTime = valueOrNow(builder.creationTime);
		this.hl7ContentId = valueOrUUID(builder.hl7ContentId);
		this.hl7RootId = valueOrUUID(builder.hl7RootId);
		this.itkContentId = valueOrUUID(builder.itkContentId);
		this.itkCorrelationId = valueOrUUID(builder.itkCorrelationId);
		this.itkDocumentId = valueOrUUID(builder.itkDocumentId);
		this.itkDocumentBody = Preconditions.checkNotNull(builder.itkDocumentBody);
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

	public Date getCreationTime() {
		return creationTime;
	}

	public String getHl7ContentId() {
		return hl7ContentId;
	}

	public String getHl7RootId() {
		return hl7RootId;
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
	
	public static Builder builder() {
		return new Builder();
	}
	
	/**
	 * Builds {@link TrunkRequestProperties} instances
	 */
	public static class Builder {
		private String mimeBoundary;
		private String ebxmlContentId;
		private String ebxmlCorrelationId;
		private Date creationTime;
		private String hl7ContentId;
		private String hl7RootId;
		private String itkContentId;
		private String itkCorrelationId;
		private String itkDocumentId;
		private String itkDocumentBody;
		
		public Builder setMimeBoundary(final String mimeBoundary) {
			this.mimeBoundary = mimeBoundary;
			return this;
		}
		
		public Builder setEbxmlContentId(final String ebxmlContentId) {
			this.ebxmlContentId = ebxmlContentId;
			return this;
		}
		
		public Builder setEbxmlCorrelationId(final String ebxmlCorrelationId) {
			this.ebxmlCorrelationId = ebxmlCorrelationId;
			return this;
		}
		
		public Builder setCreationTime(final Date creationTime) {
			this.creationTime = creationTime;
			return this;
		}
		
		public Builder setHl7ContentId(final String hl7ContentId) {
			this.hl7ContentId = hl7ContentId;
			return this;
		}
		
		public Builder setHl7RootId(final String hl7RootId) {
			this.hl7RootId = hl7RootId;
			return this;
		}
		
		public Builder setItkContentId(final String itkContentId) {
			this.itkContentId = itkContentId;
			return this;
		}
		
		public Builder setItkCorrelationId(final String itkCorrelationId) {
			this.itkCorrelationId = itkCorrelationId;
			return this;
		}
		
		public Builder setItkDocumentId(final String itkDocumentId) {
			this.itkDocumentId = itkDocumentId;
			return this;
		}
		
		public Builder setItkDocumentBody(final String itkDocumentBody) {
			this.itkDocumentBody = itkDocumentBody;
			return this;
		}
		
		public TrunkRequestProperties build() {
			return new TrunkRequestProperties(this);
		}
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