package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.builder.RouteBuilder;

import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope;

/**
 * Route to send an ITK distribution envelope over spine
 * <p>
 * Default sender properties are added to the envelope (if required) before
 * the distribution envelope is prepared for sending over spine (by creating
 * a multi-part wrapper) and storing it on a queue for sending by
 * the multi-part message sender route.
 */
public class DistributionEnvelopeSenderRoute extends RouteBuilder {
	// TODO: make URLs configurable
	
	private final String distributionEnvelopeSenderUri = "direct:distribution-envelope-sender";
	private final String multipartMessageSenderUri = "mock:multipart-message-sender";
	
	private final DistributionEnvelopePopulator distributionEnvelopePopulator = new DistributionEnvelopePopulator();

	/**
	 * Sets the prototype distribution envelope containing default properties that should
	 * be added to all envelopes before they are sent.
	 * <p>
	 * Non-empty properties on the envelope being sent are not overwritten.
	 * 
	 * @param prototype The prototype envelope
	 */
	public void setDistributionEnvelopePrototype(final DistributionEnvelope prototype) {
		distributionEnvelopePopulator.prototype = prototype;
	}
	
	
	@Override
	public void configure() throws Exception {
		configureasd();
	}
	
	private void configureasd() {
		from(distributionEnvelopeSenderUri)
			.convertBodyTo(DistributionEnvelope.class)
			.bean(distributionEnvelopePopulator, "populate")
			
			// TODO: Construct an outgoing multipart message?
			
			.to(multipartMessageSenderUri);
	}
	
	/**
	 * Populates the distribution envelope with default properties if they have
	 * not already been specified in the envelope.
	 */
	public static class DistributionEnvelopePopulator {
		private DistributionEnvelope prototype;
		
		public void populate(final DistributionEnvelope envelope) {
			final boolean overwrite = false;
			envelope.copyFrom(prototype, overwrite);
			envelope.applyDefaults();
		}
	}
}
