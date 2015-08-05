package uk.nhs.ciao.transport.spine.hl7;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

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
	private static final ThreadLocal<DateFormat> DATE_FORMAT = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("yyyyMMddHHmmss");
		}
	};
	
	private String id;
	private String creationTime;
	private String interactionId;
	private String receiverAsid;
	private String senderAsid;
	private String agentSystemAsid;
	
	public String getId() {
		return id;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public String getCreationTime() {
		return creationTime;
	}
	
	public Date getCreationTimeAsDate() throws ParseException {
		return Strings.isNullOrEmpty(creationTime) ? null : DATE_FORMAT.get().parse(creationTime);
	}
	
	public void setCreationTime(final long millis) {
		creationTime = DATE_FORMAT.get().format(new Date(millis));
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
