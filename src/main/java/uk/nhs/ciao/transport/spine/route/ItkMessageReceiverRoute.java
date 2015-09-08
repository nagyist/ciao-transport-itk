package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.xml.Namespaces;
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
public class ItkMessageReceiverRoute extends BaseRouteBuilder {
	private static final Logger LOGGER = LoggerFactory.getLogger(ItkMessageReceiverRoute.class);
	
	private String itkMessageReceiverUri;
	private String inProgressFolderManagerUri;
	
	/**
	 * URI where incoming ITK messages are received from
	 * <p>
	 * input only
	 */
	public void setItkMessageReceiverUri(final String itkMessageReceiverUri) {
		this.itkMessageReceiverUri = itkMessageReceiverUri;
	}
	
	
	/**
	 * URI of the route that will store received acknowledgements in the in-progress
	 * directory.
	 * <p>
	 * The {@link Exchange#FILE_NAME} header will be populated with a name constructed from
	 * the ack type (inf-ack, inf-nack, bus-ack, bus-nack).
	 * The body of the message is the original body of the associated ack/nack.
	 * <p>
	 * {@see InProgressFolderManagerRoute} for details of the in-progress folder structure
	 * <p>
	 * output only
	 */
	public void setInProgressFolderManagerUri(final String inProgressFolderManagerUri) {
		this.inProgressFolderManagerUri = inProgressFolderManagerUri;
	}
	
	
	/**
	 * URI of internal route to handle incoming infrastructure acks
	 * <p>
	 * input and output (internal route)
	 */
	private String getInfrastructureAckHandlerUri() {
		return internalDirectUri("infrastructure-ack-handler");
	}
	
	/**
	 * URI of internal route to handle incoming business acks
	 * <p>
	 * input and output (internal route)
	 */
	private String getBusinessAckHandlerUri() {
		return internalDirectUri("business-ack-handler");
	}	
	
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
				.when().simple("${body.containsInfrastructureAck}")
					.to(getInfrastructureAckHandlerUri())
				.endChoice()
				.when().simple("${body.containsBusinessAck}")
					.to(getBusinessAckHandlerUri())
				.endChoice()
				
				.when().simple("${body.handlingSpec.getInteration} == null")
					.log(LoggingLevel.WARN, LOGGER, "ITK message interaction not specified on handling spec")
				.endChoice()
				.otherwise()
					.log(LoggingLevel.WARN, LOGGER, "Unsupported ITK message interaction: ${body.handlingSpec.getInteration}")
				.endChoice()
			.end()
			
		.end();
	}
	
	/**
	 * Route to handle an infrastructure ack
	 */
	private void configureInfrastructureAckHandler() {
		from(getInfrastructureAckHandlerUri())
			.setBody().spel("#{body.getDecodedPayloadBody(body.payloads[0].id)}")
			.convertBodyTo(String.class)

			// Store the original body for later processing
			.setProperty("properties.originalBody").body()
			.convertBodyTo(InfrastructureResponse.class)
			.log(LoggingLevel.INFO, LOGGER, "Processing infrastructure response - trackingIdRef: ${body.trackingIdRef}, errors: ${body.errors}")
			
			.setHeader(InProgressFolderManagerRoute.Header.ACTION, constant(InProgressFolderManagerRoute.Action.STORE))
			.setHeader(InProgressFolderManagerRoute.Header.FILE_TYPE, constant(InProgressFolderManagerRoute.FileType.EVENT))
			.setHeader(InProgressFolderManagerRoute.Header.EVENT_TYPE, constant(InProgressFolderManagerRoute.EventType.MESSAGE_RECEIVED))
			.setHeader(Exchange.CORRELATION_ID).simple("${body.trackingIdRef}")
			
			.choice()
				.when().simple("${body.isAck}")
					.setHeader(Exchange.FILE_NAME).constant(InProgressFolderManagerRoute.MessageType.INFRASTRUCTURE_ACK)
				.endChoice()
				.otherwise()
					.setHeader(Exchange.FILE_NAME).constant(InProgressFolderManagerRoute.MessageType.INFRASTRUCTURE_NACK)
				.endChoice()
			.end()
			
			// Restore the original body
			.setBody().property("properties.originalBody")
			.to(inProgressFolderManagerUri)
		.end();
	}
	
	/**
	 * Route to handle an business ack
	 */
	private void configureBusinessAckHandler() {
		final Namespaces namespaces = new Namespaces("hl7", "urn:hl7-org:v3");
		
		from(getBusinessAckHandlerUri())
			.log(LoggingLevel.INFO, LOGGER, "Got an business ack")
			.setBody().spel("#{body.getDecodedPayloadBody(body.payloads[0].id)}")
			.convertBodyTo(String.class)
			.setHeader(Exchange.CORRELATION_ID).xpath("/hl7:BusinessResponseMessage/hl7:acknowledgedBy3/hl7:conveyingTransmission/hl7:id/@root", String.class, namespaces)
			.log(LoggingLevel.INFO, LOGGER, "Processing business ack - correlationId: ${headers.CamelCorrelationId}")
			
			.setHeader(InProgressFolderManagerRoute.Header.ACTION, constant(InProgressFolderManagerRoute.Action.STORE))
			.setHeader(InProgressFolderManagerRoute.Header.FILE_TYPE, constant(InProgressFolderManagerRoute.FileType.EVENT))
			.setHeader(InProgressFolderManagerRoute.Header.EVENT_TYPE, constant(InProgressFolderManagerRoute.EventType.MESSAGE_RECEIVED))
			
			.choice()
				// HL7 types codes AA and CA are ACKS
				.when().xpath("/hl7:BusinessResponseMessage/hl7:acknowledgedBy3[@typeCode='AA' or @typeCode='CA']", namespaces)
					.setHeader(Exchange.FILE_NAME).constant(InProgressFolderManagerRoute.MessageType.BUSINESS_ACK)
				.endChoice()
				.otherwise()
					.setHeader(Exchange.FILE_NAME).constant(InProgressFolderManagerRoute.MessageType.BUSINESS_NACK)
				.endChoice()
			.end()
			
			.to(inProgressFolderManagerUri)	
		.end();
	}
}
