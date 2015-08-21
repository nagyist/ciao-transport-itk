package uk.nhs.ciao.transport.spine.hl7;

import java.util.Date;
import java.util.UUID;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;

/**
 * Represents the HL7 encoded part of a Spine
 * {@link uk.nhs.ciao.transport.spine.multipart.MultipartBody} message
 * <p>
 * This class is a minimal / partial representation of the HL7 content. Enough properties are present to
 * generate the HL7 content needed in a typical outgoing multi-part spine request - however
 * incoming HL7 parts may contain additional details that are not fully parsed/represented by this class.
 */
public class HL7Part {
	/**
	 * Date format used in HL7 date-time fields
	 * <p>
	 * This is expressed in the local time-zone
	 */
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormat.forPattern("yyyyMMddHHmmss");
	
	private String id;
	private String creationTime;
	private String interactionId;
	private String receiverAsid;
	private String senderAsid;
	private String agentSystemAsid;
	
	/**
	 * Copies properties from the specified HL7 parts
	 * 
	 * @param prototype The prototype to copy from
	 * @param overwrite true if non-empty properties should be overwritten, or false if the existing values should be kept
	 */
	public void copyFrom(final HL7Part prototype, final boolean overwrite) {
		if (prototype == null) {
			return;
		}
		
		id = copyProperty(id, prototype.id, overwrite);
		creationTime = copyProperty(creationTime, prototype.creationTime, overwrite);
		interactionId = copyProperty(interactionId, prototype.interactionId, overwrite);
		receiverAsid = copyProperty(receiverAsid, prototype.receiverAsid, overwrite);
		senderAsid = copyProperty(senderAsid, prototype.senderAsid, overwrite);
		agentSystemAsid = copyProperty(agentSystemAsid, prototype.agentSystemAsid, overwrite);
	}
	
	private String copyProperty(final String original, final String prototype, final boolean overwrite) {
		return (prototype != null && (original == null || overwrite)) ? prototype : original;
	}
	
	public String getId() {
		return id;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public String getCreationTime() {
		return creationTime;
	}
	
	public Date getCreationTimeAsDate() {
		return Strings.isNullOrEmpty(creationTime) ? null : new Date(DATE_FORMAT.parseMillis(creationTime));
	}
	
	public void setCreationTime(final long millis) {
		creationTime = DATE_FORMAT.print(millis);
	}

	public void setCreationTime(final String creationTime) {
		this.creationTime = creationTime;
	}

	public String getInteractionId() {
		return interactionId;
	}

	public void setInteractionId(final String interactionId) {
		this.interactionId = interactionId;
	}

	public String getReceiverAsid() {
		return receiverAsid;
	}

	public void setReceiverAsid(final String receiverAsid) {
		this.receiverAsid = receiverAsid;
	}

	public String getSenderAsid() {
		return senderAsid;
	}

	public void setSenderAsid(final String senderAsid) {
		this.senderAsid = senderAsid;
	}

	public String getAgentSystemAsid() {
		return agentSystemAsid;
	}

	public void setAgentSystemAsid(final String agentSystemAsid) {
		this.agentSystemAsid = agentSystemAsid;
	}

	/**
	 * Generates default values for required properties which
	 * have not been specified
	 */
	public void applyDefaults() {
		if (Strings.isNullOrEmpty(id)) {
			id = generateId();
		}
		
		if (Strings.isNullOrEmpty(creationTime)) {
			setCreationTime(System.currentTimeMillis());
		}
		
		if (agentSystemAsid == null && senderAsid != null) {
			agentSystemAsid = senderAsid;
		} else if (senderAsid == null && agentSystemAsid != null) {
			senderAsid = agentSystemAsid;
		}
	}
	
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
			.add("id", id)
			.add("creationTime", creationTime)
			.add("interactionId", interactionId)
			.add("receiverAsid", receiverAsid)
			.add("senderAsid", senderAsid)
			.add("agentSystemAsid", agentSystemAsid)
			.toString();
	}
	
	protected String generateId() {
		return UUID.randomUUID().toString();
	}
}
