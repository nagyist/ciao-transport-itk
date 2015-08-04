package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.spi.TransactionErrorHandlerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope;
import uk.nhs.ciao.transport.spine.itk.InfrastructureResponse;

/**
 * Routes to handle incoming ITK messages.
 * <p>
 * The message is wrapped in the original distribution envelope - this is required
 * because some properties required to handle the incoming messages are only available
 * in the envelope. The envelope is checked and verified before it is sent to this
 * route for handling.
 * <p>
 * Business acks (if requested in the envelope) should be handled at this protocol level -
 * either in this route or in a child of this route. Infrastructure acks are handled
 * lower down the protocol stack before the message is receieved by this route.
 */
public class ITKMessageReceiverRoute extends RouteBuilder {
	private static final Logger LOGGER = LoggerFactory.getLogger(ITKMessageReceiverRoute.class);
	
	// TODO: Make in/out route URLs configurable
	
	/**
	 * URI where incoming ITK messages are received from
	 * <p>
	 * input only
	 */
	private final String itkMessageReceiverUri = "direct:itk-message-receiver";
	
	/**
	 * URI of internal route to handle incoming infrastructure acks
	 * <p>
	 * input and output (internal route)
	 */
	private final String infrastructureAckHandlerUri = "direct:infrastructure-ack-handler";
	
	/**
	 * URI of internal route to handle incoming business acks
	 * <p>
	 * input and output (internal route)
	 */
	private final String businessAckHandlerUri = "direct:business-ack-handler";
	
	@Override
	public void configure() throws Exception {
		configureITKMessageReceiver();
		configureInfrastructureAckHandler();
		configureBusinessAckHandler();
	}
	
	/**
	 * Route to receive an ITK message and send to a route for handling (determined
	 * by the interaction value)
	 */
	private void configureITKMessageReceiver() {
		from(itkMessageReceiverUri)
			.errorHandler(new TransactionErrorHandlerBuilder()
				// re-delivery attempts are handled in the publisher
				// this error handler exists to perform the processing
				// in a JMS transaction
				.disableRedelivery()
			)
			.transacted("PROPAGATION_REQUIRED")
		
			.convertBodyTo(DistributionEnvelope.class)
			.choice()
				.when().simple("${body.handlingSpec.infrastructureAck}")
					.to(infrastructureAckHandlerUri)
				.endChoice()
				.when().simple("${body.handlingSpec.businessAck}")
					.to(businessAckHandlerUri)
				.endChoice()
				.otherwise()
					.log(LoggingLevel.WARN, LOGGER, "Unsupported ITK message interaction: ${body.handlingSpec.interaction}")
				.endChoice()
			.end()
			
		.end();
	}
	
	/**
	 * Route to handle an infrastructure ack
	 */
	private void configureInfrastructureAckHandler() {
		from(infrastructureAckHandlerUri)
			.setBody().spel("#{body.getDecodedPayloadBody(body.payloads[0].id)}")
			.convertBodyTo(InfrastructureResponse.class)
			.log(LoggingLevel.INFO, LOGGER, "Got an infrastructure ack - trackingIdRef: ${body.trackingIdRef}")
		.end();
	}
	
	/**
	 * Route to handle an business ack
	 */
	private void configureBusinessAckHandler() {
		from(businessAckHandlerUri)
			.log(LoggingLevel.INFO, LOGGER, "Got an business ack")
			.setBody().spel("#{body.getDecodedPayloadBody(body.payloads[0].id)}")
			.convertBodyTo(String.class)
			.log(LoggingLevel.INFO, LOGGER, "Got an business ack: ${body}")
		.end();
	}
}
