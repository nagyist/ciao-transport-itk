package uk.nhs.ciao.transport.itk.route;

import uk.nhs.ciao.camel.BaseRouteBuilder;
import uk.nhs.ciao.transport.itk.envelope.DistributionEnvelope;

/**
 * Base route to send an ITK distribution envelope over a transport (determined by the sub-class)
 * <p>
 * Default sender properties are added to the envelope (if required) before
 * the distribution envelope is prepared for sending over the concrete transport (dependent on the type)
 */
public abstract class DistributionEnvelopeSenderRoute extends BaseRouteBuilder {
	private String distributionEnvelopeSenderUri;
	private String distributionEnvelopeResponseUri;
	
	// optional properties
	private DistributionEnvelope prototypeDistributionEnvelope;
	
	/**
	 * URI where incoming distribution envelope messages are received from
	 * <p>
	 * input only
	 */
	public void setDistributionEnvelopeSenderUri(final String distributionEnvelopeSenderUri) {
		this.distributionEnvelopeSenderUri = distributionEnvelopeSenderUri;
	}
	
	/**
	 * URI where outgoing responses for sent distribution envelopes messages are published to
	 * <p>
	 * The original ebXml message is published as the message body, along with a header describing
	 * whether the message was successfully sent or not.
	 * <p>
	 * output only
	 */
	public void setDistributionEnvelopeResponseUri(final String distributionEnvelopeResponseUri) {
		this.distributionEnvelopeResponseUri = distributionEnvelopeResponseUri;
	}
	
	/**
	 * Sets the prototype distribution envelope containing default properties that should
	 * be added to all envelopes before they are sent.
	 * <p>
	 * Non-empty properties on the envelope being sent are not overwritten.
	 * 
	 * @param prototype The prototype envelope
	 */
	public void setPrototypeDistributionEnvelope(final DistributionEnvelope prototype) {
		this.prototypeDistributionEnvelope = prototype;
	}
	
	public String getDistributionEnvelopeResponseUri() {
		return distributionEnvelopeResponseUri;
	}
	
	public String getDistributionEnvelopeSenderUri() {
		return distributionEnvelopeSenderUri;
	}
	
	public DistributionEnvelope getPrototypeDistributionEnvelope() {
		return prototypeDistributionEnvelope;
	}
	
	// Processor / bean methods
	// The methods can't live in the route builder - it causes havoc with the debug/tracer logging
	
	/**
	 * Populates the distribution envelope with default properties if they have
	 * not already been specified in the envelope.
	 */
	public class DistributionEnvelopePopulator {
		public void populateDistributionEnvelope(final DistributionEnvelope envelope) {
			final boolean overwrite = false;
			envelope.copyFrom(prototypeDistributionEnvelope, overwrite);
			envelope.applyDefaults();
		}
	}
}
