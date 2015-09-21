package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spring.spi.TransactionErrorHandlerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.nhs.ciao.camel.BaseRouteBuilder;
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
public class DistributionEnvelopeReceiverRoute extends BaseRouteBuilder {
	private static final Logger LOGGER = LoggerFactory.getLogger(DistributionEnvelopeReceiverRoute.class);
	
	private String distributionEnvelopeReceiverUri;
	private String itkMessageReceiverUri;
	private String distributionEnvelopeSenderUri;
	private IdempotentRepository<?> idempotentRepository;
	private InfrastructureResponseFactory infrastructureResponseFactory;
	
	/**
	 * URI where incoming distribution envelope messages are received from
	 * <p>
	 * input only
	 */
	public void setDistributionEnvelopeReceiverUri(final String distributionEnvelopeReceiverUri) {
		this.distributionEnvelopeReceiverUri = distributionEnvelopeReceiverUri;
	}
	
	/**
	 * URI where outgoing ITK payload messages are sent to
	 * <p>
	 * output only
	 */
	public void setItkMessageReceiverUri(final String itkMessageReceiverUri) {
		this.itkMessageReceiverUri = itkMessageReceiverUri;
	}
	
	/**
	 * URI where outgoing distribution envelope (infrastructure response) messages are sent to
	 * <p>
	 * output only
	 */
	public void setDistributionEnvelopeSenderUri(final String distributionEnvelopeSenderUri) {
		this.distributionEnvelopeSenderUri = distributionEnvelopeSenderUri;
	}
	
	public void setIdempotentRepository(final IdempotentRepository<?> idempotentRepository) {
		this.idempotentRepository = idempotentRepository;
	}
	
	public void setInfrastructureResponseFactory(final InfrastructureResponseFactory infrastructureResponseFactory) {
		this.infrastructureResponseFactory = infrastructureResponseFactory;
	}
	
	/**
	 * URI of internal route to publish outgoing payloads and to create the
	 * associated async infrastructure responses
	 * <p>
	 * input and output (internal route)
	 */
	private String getPayloadPublisherUri() {
		return internalDirectUri("distribution-envelope-payload-publisher");
	}
	
	/**
	 * URI of internal route to build and send outgoing delivery failure responses
	 * <p>
	 * input and output (internal route)
	 */
	private String getDeliveryFailureSenderUri() {
		return internalDirectUri("delivery-failure-sender");
	}
	
	/**
	 * URI of internal route to send outgoing infrastructure responses
	 * <p>
	 * input and output (internal route)
	 */
	private String getInfrastructureResponseSenderUri() {
		return internalSedaUri("infrastructure-response-sender");
	}
	
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
				.to(getPayloadPublisherUri())
			.endDoTry()
			.doCatch(Exception.class)
				.to(getDeliveryFailureSenderUri())
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
	private void configurePayloadPublisher() {
		from(getPayloadPublisherUri())
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
					.to(itkMessageReceiverUri)
				.end()
	
				// send infrastructure acknowledgement
				.doTry()
					// only send if requested (and NOT already an infrastructure response!)
					.choice().when().simple("${body.handlingSpec.infrastructureAckRequested} && ${body.containsInfrastructureAck} == false")
						.bean(infrastructureResponseFactory, "createAcknowledgmentWithEnvelope")
						.to(getInfrastructureResponseSenderUri())
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
		from(getDeliveryFailureSenderUri())
			.doTry()
				// only send if requested (and NOT already an infrastructure response!)
				.choice().when().simple("${body.handlingSpec.infrastructureAckRequested} && ${body.containsInfrastructureAck} == false")
				.bean(infrastructureResponseFactory, "createDeliveryFailureWithEnvelope")
				.to(getInfrastructureResponseSenderUri())
				.endChoice()
			.endDoTry()
			.doCatch(Exception.class)
				.log(LoggingLevel.INFO, LOGGER, "Unable to send ITK infrastructure delivery failure: ${exception}")
			.end();
	}
	
	/**
	 * Route to send outgoing infrastructure acknowledgement and delivery fault async responses
	 * <p>
	 * Processed in a separate thread via an internal seda route so as not to hold up the main processing flow
	 */
	// TODO: does outgoing ack response need retry logic / error hander? this will probably get handled by the outgoing spine component... (refactoring required)
	private void configureInfrastructureResponseSender() {
		from(getInfrastructureResponseSenderUri())
			.convertBodyTo(String.class)
			.to(distributionEnvelopeSenderUri);
	}
}
