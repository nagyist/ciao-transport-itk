package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spring.spi.TransactionErrorHandlerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope;
import uk.nhs.ciao.transport.spine.itk.InfrastructureResponseFactory;

/**
 * Routes to handle incoming ITK distribution envelopes (from spine payloads).
 * <p>
 * The incoming envelope is verified, envelope/payload published (the envelope may
 * be required for processing the payload), and async infrastructure
 * acknowledgement is sent. The processing of the underlying payload is the 
 * responsibility of another route. The infrastructure ack is sent after the payload has
 * been extracted and stored for later processing. Business acks (if requested
 * in the envelope) are the responsibility of another route.
 */
public class DistributionEnvelopeReceiverRoute extends RouteBuilder {
	private static final Logger LOGGER = LoggerFactory.getLogger(DistributionEnvelopeReceiverRoute.class);

	// TODO: Make in/out route URLs configurable
	
	/**
	 * URI where incoming distribution envelope messages are received from
	 * <p>
	 * input only
	 */
	private final String distributionEnvelopeReceiverUri = "direct:distribution-envelope-receiver";
	
	/**
	 * URI of internal route to publish outgoing payloads and to create the
	 * associated async infrastructure responses
	 * <p>
	 * input and output (internal route)
	 */
	private final String payloadPublisherUri = "direct:distribution-envelope-payload-publisher";
	
	/**
	 * URI where outgoing payload messages are sent to
	 * <p>
	 * output only
	 */
	private final String payloadDestinationUri = "mock:distribution-envelope-payloads";
	
	/**
	 * URI of internal route to build and send outgoing delivery failure responses
	 * <p>
	 * input and output (internal route)
	 */
	private final String deliveryFailureSenderUri = "direct:delivery-failure-sender";
	
	/**
	 * URI of internal route to send outgoing infrastructure responses
	 * <p>
	 * input and output (internal route)
	 */
	private final String infrastructureResponseSenderUri = "seda:infrastructure-response-sender";
	
	/**
	 * URI where outgoing infrastructure response messages are sent to
	 * <p>
	 * output only
	 */
	private final String infrastructureResponseDestinationUri = "mock:infrastructure-responses";
	
	// TODO: Make IdempotentRepository configurable
	private final IdempotentRepository<?> idempotentRepository = new MemoryIdempotentRepository();
	
	// TODO: Make InfrastructureResponseFactory configurable
	private final InfrastructureResponseFactory infrastructureResponseFactory = new InfrastructureResponseFactory();
	
	@Override
	public void configure() throws Exception {
		configureDistributionEnvelopeReceiver();
		configurePayloadPublisher();
		configureDeliveryFailureSender();
		configureInfrastructureResponseSender();
	}
	
	/**
	 * Route to receive an ITK distribution envelope message, validate the envelope - there
	 * is no synchronous response (unlike the ebXml/spine layer)
	 * <p>
	 * The payload is extracted and sent for publishing via a separate route
	 */
	private void configureDistributionEnvelopeReceiver() {
		from(distributionEnvelopeReceiverUri)
			.errorHandler(new TransactionErrorHandlerBuilder()
				// re-delivery attempts are handled in the publisher
				// this error handler exists to perform the processing
				// in a JMS transaction
				.disableRedelivery()
			)
			.transacted("PROPAGATION_REQUIRED")
			
			.convertBodyTo(DistributionEnvelope.class)
			.log(LoggingLevel.DEBUG, LOGGER, "Converted to distribution envelope: ${body}")
			.doTry()
				.process(new DistributionEnvelopeVerifier())
				.to(payloadPublisherUri)
			.endDoTry()
			.doCatch(Exception.class)
				.to(deliveryFailureSenderUri)
			.end()
			
		.end();
	}
	
	/**
	 * Route to publish the payload (and wrapping envelope) and send the corresponding async infrastructure ack
	 * <p>
	 * 'Publishing' the payload in this context means sending the payload to a resilient route 
	 * (e.g. JMS queue / data store) for later processing. The nature of the processing is
	 * determined by the type / content of the payload.
	 */
	// TODO: this route should be direct - either that or collapse into the calling route - only send NACK after retrys fail
	private void configurePayloadPublisher() {
		from(payloadPublisherUri)
			// faults and exceptions are thrown back to the caller
			// sending the delivery fault is therefore handled in the calling route
			.errorHandler(defaultErrorHandler()
					.maximumRedeliveries(4)
					.redeliveryDelay(500)
					.log(LOGGER)
					.logExhausted(true))
			.handleFault()
	
			// Publish payload message for processing - but only if not successfully processed already
			.idempotentConsumer(simple("${body.trackingId}"), idempotentRepository)
			.eager(false)
			.removeOnFailure(true)
			.skipDuplicate(false)
				// only publish if not handled already
				.filter(property(Exchange.DUPLICATE_MESSAGE).isNull())
					.to(payloadDestinationUri)
				.end()
	
				// send infrastructure acknowledgement
				.doTry()
					// only send if requested (and NOT already an infrastructure response!)
					.choice().when().simple("${body.handlingSpec.infrastructureAckRequested} && ${body.handlingSpec.infrastructureAck} == false")
						.bean(infrastructureResponseFactory, "createAcknowledgmentWithEnvelope")
						.to(infrastructureResponseSenderUri)
					.endChoice()
				.endDoTry()
				.doCatch(Exception.class)
					.log(LoggingLevel.INFO, LOGGER, "Unable to send ITK infrastructure acknowledgement: ${exception}")
				.end()
			.end()
		.end();
	}
	
	/**
	 * Route to build and send outgoing infrastructure delivery failures
	 */
	private void configureDeliveryFailureSender() {
		from(deliveryFailureSenderUri)
			.doTry()
				// only send if requested (and NOT already an infrastructure response!)
				.choice().when().simple("${body.handlingSpec.infrastructureAckRequested} && ${body.handlingSpec.infrastructureAck} == false")
				.bean(infrastructureResponseFactory, "createDeliveryFailureWithEnvelope")
				.to(infrastructureResponseSenderUri)
				.endChoice()
			.endDoTry()
			.doCatch(Exception.class)
				.log(LoggingLevel.INFO, LOGGER, "Unable to send ITK infrastructure delivery failure: ${exception}")
			.end();
	}
	
	/**
	 * Route to send outgoing infrastructure acknowledgement and delivery fault async responses
	 */
	// TODO: does outgoing ack response need retry logic / error hander? this will probably get handled by the outgoing spine component... (refactoring required)
	private void configureInfrastructureResponseSender() {
		from(infrastructureResponseSenderUri)
			.convertBodyTo(String.class)
			.to(infrastructureResponseDestinationUri);
	}
}
