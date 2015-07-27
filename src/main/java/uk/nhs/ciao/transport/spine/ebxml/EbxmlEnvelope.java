package uk.nhs.ciao.transport.spine.ebxml;

import java.util.List;
import java.util.UUID;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

/**
 * Simplified bean-like view of a SOAP+ebXml envelope 
 * <p>
 * This makes various assumptions about the incoming/outgoing messages to flatten the overall
 * structure (e.g. a single fromParty / toParty) etc..
 */
public class EbxmlEnvelope {
	private String fromParty;
	private String toParty;	
	private String cpaId;
	private String conversationId;
	private String service;
	private String action;
	private final MessageData messageData = new MessageData(); // required
	private boolean acknowledgment;
	private Error error;
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

	public Error getError() {
		return error;
	}

	public Error addError() {
		this.error = new Error();
		return error;
	}
	
	/**
	 * Tests if this envelope represents a SOAP fault (i.e. contains
	 * an error element)
	 */
	public boolean isSOAPFault() {
		return error != null;
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
	
	protected String generateId() {
		return UUID.randomUUID().toString();
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
			
			// TODO: timestamp
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
	
	public class Error {
		private String listId;
		private String id;
		private String code;
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
