package uk.nhs.ciao.transport.dts.route;

import static uk.nhs.ciao.logging.CiaoCamelLogMessage.camelLogMsg;

import org.apache.camel.Exchange;

import uk.nhs.ciao.dts.ControlFile;
import uk.nhs.ciao.logging.CiaoCamelLogger;
import uk.nhs.ciao.transport.itk.envelope.DistributionEnvelope;
import uk.nhs.ciao.transport.itk.route.DistributionEnvelopeSenderRoute;

public class DTSDistributionEnvelopeSenderRoute extends DistributionEnvelopeSenderRoute {
	private static final CiaoCamelLogger LOGGER = CiaoCamelLogger.getLogger(DTSDistributionEnvelopeSenderRoute.class);
	
	private String dtsMessageSenderUri;
	private String dtsMessageSendResponseReceiverUri;
	
	/**
	 * URI of the DTS outbox
	 * <p>
	 * output only
	 */
	public void setDTSMessageSenderUri(String dtsMessageSenderUri) {
		this.dtsMessageSenderUri = dtsMessageSenderUri;
	}
	
	/**
	 * URI of the DTS sent message box
	 * <p>
	 * input only
	 */
	public void setDTSMessageSendResponseReceiverUri(final String dtsMessageSendResponseReceiverUri) {
		this.dtsMessageSendResponseReceiverUri = dtsMessageSendResponseReceiverUri;
	}
	
	@Override
	public void configure() throws Exception {
		configureRequestSender();
		configureSendResponseReceiver();
	}
	
	private void configureRequestSender() throws Exception {
		/*
		 * The output will be a multipart body - individual parts are constructed
		 * and stored as properties until the body is built in the final stage 
		 */
		from(getDistributionEnvelopeSenderUri())				
			// Configure the distribution envelope
			.convertBodyTo(DistributionEnvelope.class)
			.bean(new DistributionEnvelopePopulator())
			.setProperty("distributionEnvelope").body()
			
			// Resolve destination address
//			.bean(new DestinationAddressBuilder())
//			.to(spineEndpointAddressEnricherUrl)
//			.setProperty("destination").body()

			// Configure control file
//			.bean(new EbxmlManifestBuilder())
//			.setHeader(Exchange.CORRELATION_ID).simple("${body.messageData.messageId}")
//			.setHeader("SOAPAction").simple("${body.service}/${body.action}")
//			.setProperty("ebxmlManifest").body()
			
			.process(LOGGER.info(camelLogMsg("Constructed DTS message for sending")
				.documentId(header(Exchange.CORRELATION_ID)) // TODO - where will this come from? The thread is not blocked like on the spine transport
				.itkTrackingId("${property.distributionEnvelope.trackingId}")
				.distributionEnvelopeService("${property.distributionEnvelope.service}")
				.interactionId("${property.distributionEnvelope.handlingSpec.getInteration}")
//				.ebxmlMessageId("${property.ebxmlManifest.messageData.messageId}")
//				.service("${property.ebxmlManifest.service}")
//				.action("${property.ebxmlManifest.action}")
//				.receiverAsid("${property.destination?.asid}")
//				.receiverODSCode("${property.destination?.odsCode}")
//				.receiverMHSPartyKey("${property.ebxmlManifest.toParty}")
				.eventName("constructed-dts-message")))
			
			.convertBodyTo(String.class)
			
			// write data file, then control file
			// write through a temporary folder (to avoid the client process reading files before they are fully written)
			
			.to(dtsMessageSenderUri)
		.end();
	}
	
	private void configureSendResponseReceiver() throws Exception {
		from(dtsMessageSendResponseReceiverUri)
			// only process each file once - use an idempotent repository
		
			.setProperty("original-body").body() // maintain original serialised form
			.convertBodyTo(ControlFile.class)
			
			.process(LOGGER.info(camelLogMsg("Received DTS message send notification")
				.documentId(header(Exchange.CORRELATION_ID))
//				.ebxmlMessageId("${body.messageData.messageId}")
//				.ebxmlRefToMessageId("${body.messageData.refToMessageId}")
//				.service("${body.service}")
//				.action("${body.action}")
//				.receiverMHSPartyKey("${body.toParty}")
				.eventName("received-dts-sent-response")))
			
			.choice()
				.when().simple("body.isAcknowledgment")
					.setHeader("ciao.messageSendNotification", constant("sent"))
				.otherwise()
					.setHeader("ciao.messageSendNotification", constant("send-failed"))
				.endChoice()
			.end()
			
			.setBody().property("original-body") // restore original serialised form
			.to(getDistributionEnvelopeResponseUri())
		.end();
	}
}
