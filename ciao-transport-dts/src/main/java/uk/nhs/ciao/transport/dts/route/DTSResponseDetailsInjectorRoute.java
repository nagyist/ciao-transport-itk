package uk.nhs.ciao.transport.dts.route;

import static uk.nhs.ciao.transport.dts.route.DTSHeaders.*;

import java.util.Map;

import org.apache.camel.Header;
import org.apache.camel.Headers;
import org.apache.camel.util.toolbox.AggregationStrategies;

import com.google.common.base.Strings;

import uk.nhs.ciao.camel.BaseRouteBuilder;

/**
 * Route which intercepts ITK response messages (send by the DistributionEnvelopeReceiverRoute) and
 * injects the DTS control file workflowId and address details derived from the original request control file.
 */
public class DTSResponseDetailsInjectorRoute extends BaseRouteBuilder {
	private String fromDistributionEnvelopeReceiverUri;
	private String toDistributionEnvelopeSenderUri;
	
	public void setFromDistributionEnvelopeReceiverUri(final String fromDistributionEnvelopeReceiverUri) {
		this.fromDistributionEnvelopeReceiverUri = fromDistributionEnvelopeReceiverUri;
	}
	
	public void setToDistributionEnvelopeSenderUri(final String toDistributionEnvelopeSenderUri) {
		this.toDistributionEnvelopeSenderUri = toDistributionEnvelopeSenderUri;
	}
	
	@Override
	public void configure() throws Exception {
		from(fromDistributionEnvelopeReceiverUri)
			// Using multicast/pipeline to maintain original message
			.multicast(AggregationStrategies.useOriginal())
				.pipeline()
					.bean(new ResponseDetailsInjector())
					.to(toDistributionEnvelopeSenderUri)
				.end()
			.end()
		.end();
	}
	
	/**
	 * Outgoing message is response - so invert from/to addresses
	 * <p>
	 * The workflowId is echoed from request to response (for now!) so it
	 * can be left as is - this may need to change in the future if the
	 * pattern used for worflowIds change.
	 */
	public static class ResponseDetailsInjector {
		public void injectWorkflowDetails(@Headers final Map<String, Object> headers,
				@Header(HEADER_FROM_DTS) final String fromDTS,
				@Header(HEADER_TO_DTS) final String toDTS) {
			
			if (Strings.isNullOrEmpty(fromDTS)) {
				headers.remove(HEADER_TO_DTS);
			} else {
				headers.put(HEADER_TO_DTS, fromDTS);
			}

			if (Strings.isNullOrEmpty(toDTS)) {
				headers.remove(HEADER_FROM_DTS);
			} else {
				headers.put(HEADER_FROM_DTS, toDTS);
			}
		}
	}
}
