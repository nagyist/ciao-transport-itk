package uk.nhs.ciao.transport.spine.trunk;

import static com.google.common.base.Preconditions.checkNotNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.UUID;

import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.docs.parser.ParsedDocument;
import uk.nhs.ciao.exceptions.CIAOConfigurationException;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;

/**
 * Bean to declare variables required by freemarker
 */
public class TrunkRequestProperties {	
	private static final String DEFAULT_MIME_BOUNDARY = "--=_MIME-Boundary";
	
	/**
	 * Date format used in ebXml time-stamps
	 * <p>
	 * This is expressed in the UTC time-zone
	 */
	private static final ThreadLocal<DateFormat> EBXML_TIMESTAMP_FORMAT = new ThreadLocal<DateFormat>() {
		@Override
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
		@Override
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
	
	public TrunkRequestProperties(final CIAOConfig config, final ParsedDocument parsedDocument) throws CIAOConfigurationException {
		final ValueFinder values = new ValueFinder(config, parsedDocument);
		
		this.mimeBoundary = values.valueOrDefault("mimeBoundary", DEFAULT_MIME_BOUNDARY);
		this.ebxmlContentId = values.valueOrUUID("ebxmlContentId");
		this.ebxmlCorrelationId = values.valueOrUUID("ebxmlCorrelationId");
		this.creationTime = values.valueOrNow("creationTime");
		this.hl7ContentId = values.valueOrUUID("hl7ContentId");
		this.hl7RootId = values.valueOrUUID("hl7RootId");
		this.itkContentId = values.valueOrUUID("itkContentId");
		this.itkCorrelationId = values.valueOrUUID("itkCorrelationId");
		this.itkDocumentId = values.valueOrUUID("itkDocumentId");
		
		this.senderPartyId = values.requiredValue("senderPartyId");
		this.senderAsid = values.requiredValue("senderAsid");
		this.senderODSCode = values.requiredValue("senderODSCode");
		
		this.receiverPartyId = values.requiredValue("receiverPartyId");
		this.receiverAsid = values.requiredValue("receiverAsid");
		this.receiverODSCode = values.requiredValue("receiverODSCode");
		this.receiverCPAId = values.requiredValue("receiverCPAId");
		
		this.auditODSCode = values.valueOrDefault("auditODSCode", this.senderODSCode);
		
		this.interactionId = values.requiredValue("interactionId");
		this.itkProfileId = values.requiredValue("itkProfileId");
		this.itkHandlingSpec = values.requiredValue("itkHandlingSpec");
		
		// TODO: compress and encode as Base64
		this.itkDocumentBody = new String(parsedDocument.getOriginalDocument().getContent());
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
	
	private static class ValueFinder {
		/**
		 * To allow property lookups to be handled in a case insensitive way (and to handle defaults from CIAOConfig)
		 * a lookup map is built.
		 * <p>
		 * When there are name collisions, e.g. a property is specified in both CIAOConfig and the parsed document properties or
		 * a name is defined in multiple cases, the previous property value is overwritten.
		 */
		private final Map<String, String> valuesByName;
		
		public ValueFinder(final CIAOConfig config, final ParsedDocument parsedDocument) throws CIAOConfigurationException {
			valuesByName = Maps.newHashMap();
			
			// Add defaults (from CIAOConfig)
			for (final String key: config.getConfigKeys()) {
				addValue(key, config.getConfigValue(key));
			}
			
			// Overwrite with properties from the incoming document
			for (final Entry<String, Object> entry: parsedDocument.getProperties().entrySet()) {
				if (entry.getValue() instanceof String) {
					addValue(entry.getKey(), (String)entry.getValue());
				}
			}
		}
		
		private void addValue(final String name, final String value) {
			if (!Strings.isNullOrEmpty(value)) {
				valuesByName.put(name.toLowerCase(), value);
			}
		}
		
		public String requiredValue(final String name) {
			return checkNotNull(value(name));
		}
		
		public String value(final String name) {
			return valuesByName.get(name.toLowerCase());
		}
		
		public String valueOrDefault(final String name, final String defaultValue) {
			final String value = value(name);
			return Strings.isNullOrEmpty(value) ? defaultValue : value;
		}
		
		public String valueOrUUID(final String name) {
			final String value = value(name);
			return Strings.isNullOrEmpty(value) ? UUID.randomUUID().toString() : value;
		}
		
		public Date valueOrNow(final String name) {
			// TODO: Work out date formats
//			final String value = value(name);
//			return value == null ? new Date() : value;
			return new Date();
		}
	}
}