package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope;

/**
 * Processor which verifies that a specified distribution envelope is valid
 */
public class DistributionEnvelopeVerifier implements Processor {
	@Override
	public void process(final Exchange exchange) throws Exception {
		final DistributionEnvelope envelope = exchange.getIn().getMandatoryBody(DistributionEnvelope.class);
		
		if (envelope.getPayloads().isEmpty()) {
			// TODO: how should an error be handled - as a fault message or throw exception?
		}
	}
}
