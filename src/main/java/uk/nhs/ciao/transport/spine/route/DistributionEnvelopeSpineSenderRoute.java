package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.builder.RouteBuilder;

import com.google.common.base.Strings;

import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope;
import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope.Address;
import uk.nhs.ciao.transport.spine.itk.Identity;

/**
 * Route to send an ITK distribution envelope over spine
 * <p>
 * Default sender properties are added to the envelope (if required) before
 * the distribution envelope is prepared for sending over spine (by creating
 * a multi-part wrapper) and storing it on a queue for sending by
 * the multi-part message sender route.
 */
public class DistributionEnvelopeSpineSenderRoute extends RouteBuilder {
	// TODO: make URLs configurable
	
	private final String distributionEnvelopeSenderUri = "direct:distribution-envelope-sender";
	private final String multipartMessageSenderUri = "mock:multipart-message-sender";
	
	private DistributionEnvelopePopulator populator = new DistributionEnvelopePopulator();

	public DistributionEnvelopePopulator getPopulator() {
		return populator;
	}
	
	public void setPopulator(final DistributionEnvelopePopulator populator) {
		this.populator = populator;
	}
	
	@Override
	public void configure() throws Exception {
		configureasd();
	}
	
	private void configureasd() {
		from(distributionEnvelopeSenderUri)
			.convertBodyTo(DistributionEnvelope.class)
			.bean(populator, "populate")
			
			// TODO: Construct an outgoing multipart message?
			
			.to(multipartMessageSenderUri);
	}
	
	/**
	 * Populates the distribution envelope with default properties if they have
	 * not already been specified in the envelope.
	 */
	public static class DistributionEnvelopePopulator {
		private Identity auditIdentity;
		private Address senderAddress;
		private String service;
		
		public void setAuditIdentity(final Identity auditIdentity) {
			this.auditIdentity = auditIdentity;
		}
		
		public void setSenderAddress(final Address senderAddress) {
			this.senderAddress = senderAddress;
		}
		
		public void setService(final String service) {
			this.service = service;
		}
		
		public void populate(final DistributionEnvelope envelope) {
			if (auditIdentity != null && envelope.getAuditIdentity() == null) {
				envelope.setAuditIdentity(new Identity(auditIdentity));
			}
			
			if (senderAddress != null && envelope.getSenderAddress() == null) {
				envelope.setSenderAddress(new Address(senderAddress));
			}
			
			if (service != null && Strings.isNullOrEmpty(envelope.getService())) {
				envelope.setService(service);
			}
			
			envelope.applyDefaults();
		}
	}
}
