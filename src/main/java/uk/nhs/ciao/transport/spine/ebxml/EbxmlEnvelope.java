package uk.nhs.ciao.transport.spine.ebxml;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

/**
 * Simplified bean-like view of a SOAP+ebXml envelope 
 * <p>
 * This makes various assumptions about the incoming/outgoing messages to flatten the overall
 * structure (e.g. a single fromParty / toParty) etc..
 */
public class EbxmlEnvelope {
	public static final String ACTION_ACKNOWLEDGMENT = "Acknowledgment";	
	public static final String ACTION_MESSAGE_ERROR = "MessageError";
	
	public static final String SERVICE_EBXML_MSG = "urn:oasis:names:tc:ebxml-msg:service";
	
	public static final String ERROR_CODE_DELIVERY_FAILURE = "DeliveryFailure";
	
	public static final String ERROR_SEVERITY_ERROR = "Error";
	public static final String ERROR_SEVERITY_WARNING = "Warning";
	
	/**
	 * Date format used in ebXml time-stamps
	 * <p>
	 * This is expressed in the UTC time-zone
	 */
	private static final ThreadLocal<DateFormat> TIMESTAMP_FORMAT = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
			return dateFormat;
		}
	};
	
	private String fromParty;
	private String toParty;	
	private String cpaId;
	private String conversationId;
	private String service;
	private String action;
	private final MessageData messageData = new MessageData(); // required
	private boolean acknowledgment;
	private ErrorDetail error;
	private final List<ManifestReference> manifestReferences = Lists.newArrayList();
	
	public String getFromParty() {
		return fromParty;
	}

	public void setFromParty(final String fromParty) {
		this.fromParty = fromParty;
	}

	public String getToParty() {
		return toParty;
	}

	public void setToParty(final String toParty) {
		this.toParty = toParty;
	}

	public String getCpaId() {
		return cpaId;
	}

	public void setCpaId(final String cpaId) {
		this.cpaId = cpaId;
	}

	public String getConversationId() {
		return conversationId;
	}

	public void setConversationId(final String conversationId) {
		this.conversationId = conversationId;
	}

	public String getService() {
		return service;
	}

	public void setService(final String service) {
		this.service = service;
	}

	public String getAction() {
		return action;
	}

	public void setAction(final String action) {
		this.action = action;
	}

	public boolean isAcknowledgment() {
		return acknowledgment;
	}

	public void setAcknowledgment(final boolean acknowledgment) {
		this.acknowledgment = acknowledgment;
	}

	public ErrorDetail getError() {
		return error;
	}

	public ErrorDetail addError() {
		this.error = new ErrorDetail();
		return error;
	}
	
	/**
	 * Tests if this envelope represents a SOAP fault (i.e. contains
	 * an error element and is not an acknowledgment)
	 */
	public boolean isSOAPFault() {
		return error != null && !acknowledgment;
	}
	
	public List<ManifestReference> getManifestReferences() {
		return manifestReferences;
	}
	
	public ManifestReference addManifestReference() {
		final ManifestReference reference = new ManifestReference();
		manifestReferences.add(reference);
		return reference;
	}
	
	/**
	 * Tests if this envelope represents a manifest (i.e. contains manifest references)
	 */
	public boolean isManifest() {
		return !manifestReferences.isEmpty();
	}

	public MessageData getMessageData() {
		return messageData;
	}
	
	/**
	 * Generates default values for required properties which
	 * have not been specified
	 */
	public void applyDefaults() {
		messageData.applyDefaults();
		if (error != null) {
			error.applyDefaults();
		}
	}
	
	/**
	 * Creates a new envelope representing an acknowledgment associated with this message.
	 * <p>
	 * Standard reply message fields are populated from this envelope and required fields (e.g. messageId)
	 * are generated.
	 *
	 * @return A new acknowledgment instance
	 */
	public EbxmlEnvelope generateAcknowledgment() {
		final EbxmlEnvelope ack = generateBaseReply();		
		ack.action = ACTION_ACKNOWLEDGMENT;
		ack.acknowledgment = true;
		
		ack.applyDefaults();
		
		return ack;
	}
	
	/**
	 * Creates a new envelope representing an acknowledgment of delivery failure associated with this message.
	 * <p>
	 * Standard reply message fields are populated from this envelope and required fields (e.g. messageId)
	 * are generated. In addition to the standard acknowledgment properties, error properties with
	 * details of the delivery failure are included
	 *
	 * @return A new delivery failure notification instance
	 */
	public EbxmlEnvelope generateDeliveryFailureNotification(final String codeContext, final String description) {
		final EbxmlEnvelope ack = generateBaseReply();		
		ack.action = ACTION_ACKNOWLEDGMENT;
		ack.acknowledgment = true;

		ack.addError();
		ack.error.setDeliveryFailure();
		ack.error.codeContext = codeContext;
		ack.error.description = description;
		
		ack.applyDefaults();
		
		return ack;
	}
	
	/**
	 * Creates a new envelope representing a SOAPFault associated with this message.
	 * <p>
	 * Standard reply message fields are populated from this envelope and required fields (e.g. messageId)
	 * are generated.
	 *
	 * @return A new SOAPFault instance
	 */
	public EbxmlEnvelope generateSOAPFault(final String codeContext, final String code, final String description) {
		final EbxmlEnvelope fault = generateBaseReply();
		fault.action = ACTION_MESSAGE_ERROR;
		
		fault.addError();
		fault.error.codeContext = codeContext;
		fault.error.code = code;
		fault.error.description = description;
		
		fault.applyDefaults();
		
		return fault;
	}
	
	/**
	 * Creates a new envelope instance and copies standard 'reply' fields into it
	 * <p>
	 * Note to and from are inverted (since it is a reply)
	 * 
	 * @return The reply instance
	 */
	private EbxmlEnvelope generateBaseReply() {
		final EbxmlEnvelope reply = new EbxmlEnvelope();
		
		reply.fromParty = toParty;
		reply.toParty = fromParty;
		reply.cpaId = cpaId;
		reply.conversationId = conversationId;
		reply.service = SERVICE_EBXML_MSG;
		reply.messageData.refToMessageId = messageData.messageId;
		
		return reply;
	}
	
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
			.add("fromParty", fromParty)
			.add("toParty", toParty)
			.add("cpaId", cpaId)
			.add("conversationId", conversationId)
			.add("service", service)
			.add("action", action)
			.add("messageData", messageData)
			.add("acknowledgment", acknowledgment)
			.add("error", error)
			.add("manifestReferences", manifestReferences)
			.toString();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return messageData.messageId.hashCode();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Envelopes are considered equal if they have the same messageId
	 */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		} else if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		
		final EbxmlEnvelope other = (EbxmlEnvelope) obj;
		return Objects.equal(messageData.messageId, other.messageData.messageId);
	}

	protected String generateId() {
		return UUID.randomUUID().toString();
	}
	
	protected String generateTimestamp() {
		return TIMESTAMP_FORMAT.get().format(new Date());
	}

	public class MessageData {
		private String messageId; // required
		private String timestamp; // required
		private String refToMessageId; // required if ERROR message
		
		public String getMessageId() {
			return messageId;
		}
		
		public void setMessageId(final String messageId) {
			this.messageId = messageId;
		}
		
		public String getTimestamp() {
			return timestamp;
		}
		
		public void setTimestamp(final String timestamp) {
			this.timestamp = timestamp;
		}
		
		public String getRefToMessageId() {
			return refToMessageId;
		}
		
		public void setRefToMessageId(final String refToMessageId) {
			this.refToMessageId = refToMessageId;
		}
		
		/**
		 * Generates default values for required properties which
		 * have not been specified
		 */
		public void applyDefaults() {
			if (Strings.isNullOrEmpty(messageId)) {
				messageId = generateId();
			}
			
			if (Strings.isNullOrEmpty(timestamp)) {
				timestamp = generateTimestamp();
			}
		}
		
		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this)
				.add("messageId", messageId)
				.add("timestamp", timestamp)
				.add("refToMessageId", refToMessageId)
				.toString();
		}
	}
	
	public class ErrorDetail {
		private String listId;
		private String id;
		private String code;
		private String severity;
		private String codeContext;
		private String description;
		
		/**
		 * Generates default values for required properties which
		 * have not been specified
		 */
		public void applyDefaults() {
			if (Strings.isNullOrEmpty(listId)) {
				listId = generateId();
			}
			
			if (Strings.isNullOrEmpty(id)) {
				id = generateId();
			}
			
			if (Strings.isNullOrEmpty(severity)) {
				setError();
			}
		}
		
		public String getListId() {
			return listId;
		}
		
		public void setListId(final String listId) {
			this.listId = listId;
		}
		
		public String getId() {
			return id;
		}
		
		public void setId(final String id) {
			this.id = id;
		}
		
		public String getCode() {
			return code;
		}
		
		public void setCode(final String code) {
			this.code = code;
		}
		
		public String getSeverity() {
			return severity;
		}
		
		public void setSeverity(final String severity) {
			this.severity = severity;
		}
		
		public boolean isDeliveryFailure() {
			return ERROR_CODE_DELIVERY_FAILURE.equalsIgnoreCase(code);
		}
		
		public void setDeliveryFailure() {
			setCode(ERROR_CODE_DELIVERY_FAILURE);
		}
		
		public boolean isError() {
			return ERROR_SEVERITY_ERROR.equalsIgnoreCase(severity);
		}
		
		public void setError() {
			setSeverity(ERROR_SEVERITY_ERROR);
		}
		
		public boolean isWarning() {
			return ERROR_SEVERITY_WARNING.equalsIgnoreCase(severity);
		}
		
		public void setWarning() {
			setSeverity(ERROR_SEVERITY_WARNING);
		}
		
		public String getCodeContext() {
			return codeContext;
		}
		
		public void setCodeContext(final String codeContext) {
			this.codeContext = codeContext;
		}
		
		public String getDescription() {
			return description;
		}
		
		public void setDescription(final String description) {
			this.description = description;
		}
		
		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this)
				.add("listId", listId)
				.add("id", id)
				.add("code", code)
				.add("severity", severity)
				.add("codeContext", codeContext)
				.add("description", description)
				.toString();
		}
	}
	
	public class ManifestReference {
		private String href;
		private boolean hl7;
		private String description;
		
		public String getHref() {
			return href;
		}
		
		public void setHref(String href) {
			this.href = href;
		}
		
		public boolean isHl7() {
			return hl7;
		}
		
		public void setHl7(final boolean hl7) {
			this.hl7 = hl7;
		}
		
		public String getDescription() {
			return description;
		}
		
		public void setDescription(String description) {
			this.description = description;
		}
		
		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this)
				.add("href", href)
				.add("hl7", hl7)
				.add("description", description)
				.toString();
		}
	}
}
