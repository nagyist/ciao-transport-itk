package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.spring.spi.TransactionErrorHandlerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.nhs.ciao.camel.CamelApplication;
import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.docs.parser.ParsedDocument;
import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope;
import uk.nhs.ciao.transport.spine.multipart.MultipartBody;
import uk.nhs.ciao.transport.spine.trunk.TrunkRequestPropertiesFactory;

/**
 * Configures multiple camel CDA builder routes determined by properties specified
 * in the applications registered {@link CIAOConfig}.
 */
public class LegacySpineTransportRoutes extends RouteBuilder {
	private static final Logger LOGGER = LoggerFactory.getLogger(LegacySpineTransportRoutes.class);
	private static final String ITK_ACK_RECEIVER_URL = "direct:asyncItkAcks";

	/**
	 * Creates multiple document parser routes
	 * 
	 * @throws RuntimeException If required CIAO-config properties are missing
	 */
	@Override
	public void configure() throws Exception {
		//configureTrunkRequestBuilder();
		configureItkAckReceiver();
	}
	
	/**
	 * Document input route (creates a multipart request wrapper for the incoming document)
	 * <ul>
	 * <li>Transforms the ParsedDocument json into a multi-part trunk request message
	 * <li>Adds the trunk request message onto a JMS queue for later processing (by any running process)
	 */
	private void configureTrunkRequestBuilder() {
		final CIAOConfig config = CamelApplication.getConfig(getContext());
		
		from("jms:queue:{{documentQueue}}?destination.consumer.prefetchSize=0")
		.id("trunk-request-builder")
		.errorHandler(new TransactionErrorHandlerBuilder()
			.asyncDelayedRedelivery()
			.maximumRedeliveries(2)
			.backOffMultiplier(2)
			.redeliveryDelay(2000)
			.log(LOGGER)
			.logExhausted(true)
		)
		.transacted("PROPAGATION_NOT_SUPPORTED")
		.unmarshal().json(JsonLibrary.Jackson, ParsedDocument.class)
		.bean(new TrunkRequestPropertiesFactory(config), "newTrunkRequestProperties")
		.setHeader("SOAPAction").simple("urn:nhs:names:services:itk/{{interactionId}}")
		.setHeader(Exchange.CONTENT_TYPE).simple("multipart/related; boundary=\"${body.mimeBoundary}\"; type=\"text/xml\"; start=\"<${body.ebxmlContentId}>\"")
		.setHeader(Exchange.CORRELATION_ID).simple("${body.ebxmlCorrelationId}")
		.to("freemarker:uk/nhs/ciao/transport/spine/trunk/TrunkRequest.ftl")
		.to("jms:queue:{{trunkRequestQueue}}");
	}
	
	/**
	 * Incoming ITK trunk ACK receiver route
	 * <ul>
	 * <li>Receives ebXml acks from a direct route (but originally from an HTTP request) [sync]
	 * <li>TODO:
	 */
	private void configureItkAckReceiver() {
		
		/*
		 * Notes about SOAP + HTTP error codes:
		 * http://www.ws-i.org/Profiles/BasicProfile-1.0-2004-04-16.html#refinement16488480
		 * 
		 * Use 4** series for client errors (e.g. invalid media type, invalid method, unparsable content
		 * (not multipart / not xml part, etc)
		 * 
		 * Use 5** series for server errors. In particular:
		 * R1126 An INSTANCE MUST use a "500 Internal Server Error" HTTP status code if the response message is a SOAP Fault.
		 * 
		 * This seems to imply that:
		 *   if the request fails to parse into at least multipart with first part == soap/ebXml
		 *   -> respond with suitable 4** series error - type of body is not explicitly specified (it could just be a text version of the error code)
		 *   
		 *   if as a minimum the ebXml can be parsed and there is some other problem
		 *   -> respond with 500 error and use a SOAPFault style message
		 *   
		 *   otherwise
		 *   -> respond with 202 (Accepted) + empty body
		 *   -> then send async ebXml ack/nack
		 */
		
		from(ITK_ACK_RECEIVER_URL)
		.id("itk-ack-receiver")
		.convertBodyTo(MultipartBody.class)
		.split().simple("${body.parts[0]}")
			.convertBodyTo(EbxmlEnvelope.class)
		.end()
		.log("ITK trunk ACK receieved: handling is not yet completed");
		
		// First - need to check the ebxml part (part 1)
		// if there is an error - what should the HTTP response be? 400 (BAD_REQUEST)?
		// .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("202"))
		// if there is no error respond with 202 (ACCEPTED)
		// .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("202"))
		
		// then via a seda / asyn route (to let the original request complete!)
		// respond to the ebxml part with an ebxml ack or nack
		// if ebxml ack -> handle ITK message
	}
	
	// TODO: What if an incoming ITK business ack requests an INF ack?
}
