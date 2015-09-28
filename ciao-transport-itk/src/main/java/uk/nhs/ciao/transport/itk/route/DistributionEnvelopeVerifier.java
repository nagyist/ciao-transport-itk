package uk.nhs.ciao.transport.itk.route;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import uk.nhs.ciao.transport.itk.envelope.DistributionEnvelope;

/**
 * Processor which verifies that a specified distribution envelope is valid
 */
public class DistributionEnvelopeVerifier implements Processor {
	@Override
	public void process(final Exchange exchange) throws Exception {
		final DistributionEnvelope envelope = exchange.getIn().getMandatoryBody(DistributionEnvelope.class);
		
		if (envelope.getPayloads().isEmpty()) {
			throw new Exception("No payload found in distribution envelope");
		}
	}
}
