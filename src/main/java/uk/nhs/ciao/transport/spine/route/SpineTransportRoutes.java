package uk.nhs.ciao.transport.spine.route;

import static uk.nhs.ciao.docs.parser.HeaderNames.*;
import static uk.nhs.ciao.transport.spine.forwardexpress.ForwardExpressSenderRoutes.*;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.spring.spi.TransactionErrorHandlerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

import uk.nhs.ciao.CIPRoutes;
import uk.nhs.ciao.camel.CamelApplication;
import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.docs.parser.ParsedDocument;
import uk.nhs.ciao.transport.spine.forwardexpress.EbxmlAcknowledgementProcessor;
import uk.nhs.ciao.transport.spine.multipart.MultipartBody;
import uk.nhs.ciao.transport.spine.trunk.TrunkRequestPropertiesFactory;

/**
 * Configures multiple camel CDA builder routes determined by properties specified
 * in the applications registered {@link CIAOConfig}.
 */
public class SpineTransportRoutes extends CIPRoutes {
	private static final Logger LOGGER = LoggerFactory.getLogger(SpineTransportRoutes.class);
	private static final String EBXML_ACK_RECEIVER_URL = "direct:asyncEbxmlAcks";
	private static final String ITK_ACK_RECEIVER_URL = "direct:asyncItkAcks";
	private final Namespaces namespaces;
	
	public SpineTransportRoutes() {
		namespaces = new Namespaces("soap", "http://schemas.xmlsoap.org/soap/envelope/");
		namespaces.add("eb", "http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd");
	}
	
	/**
	 * Creates multiple document parser routes
	 * 
	 * @throws RuntimeException If required CIAO-config properties are missing
	 */
	@Override
	public void configure() {
		super.configure();

		configureTrunkRequestBuilder();
		configureTrunkRequestSender();
		configureSpineSender();
		configureHttpServer();
		configureEbxmlAckReceiver();
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
	 * Outgoing trunk request message queue
	 * <ul>
	 * <li>Stores a copy of the outgoing request message in the document's in-progress folder
	 * <li>Sends a multi-part trunk request message over the spine
	 * <li>Blocks until an async ebXml ack is received off a configured JMS topic or a timeout occurs
	 * <li>Marks message as success, retry or failure based on the ACK content
	 */
	private void configureTrunkRequestSender() {
		from("jms:queue:{{trunkRequestQueue}}?destination.consumer.prefetchSize=0")
		.id("trunk-request-sender")
		.errorHandler(new TransactionErrorHandlerBuilder()
			.asyncDelayedRedelivery()
			.maximumRedeliveries(2)
			.backOffMultiplier(2)
			.redeliveryDelay(2000)
			.log(LOGGER)
			.logExhausted(true)
		)
		.transacted("PROPAGATION_NOT_SUPPORTED")
		
		// Store a copy of the outgoing request into the documents in-progress folder
		.setHeader(Exchange.FILE_NAME, simple("${header." + IN_PROGRESS_FOLDER + "}/trunk-request"))
		.to("file://.") // full location is determined by Exchange.FILE_NAME header
		
//		.doTry()
			.to("spine:trunk")
			.process(new EbxmlAcknowledgementProcessor())
//		.endDoTry()
//		.doCatch(HttpOperationFailedException.class)
//			.process(new HttpErrorHandler())
		;
	}
	
	/**
	 * Route to send an HTTP request/response to spine and wait
	 * for a related asynchronous ack message.
	 * <p>
	 * The thread sending a message to this route will <strong>block</strong> until:
	 * <ul>
	 * <li>the original request-response fails
	 * <li>or a timeout occurs while waiting for the ack
	 * <li>or the ack is received
	 */
	private void configureSpineSender() {
		final String name = "trunk";
		final String requestRouteId = name;
		final String ackRouteId = name + "-ack";
		final String aggregateRouteId = name + "-aggregate";
		
		try {
			forwardExpressSender(getContext(),
				from("spine:" + requestRouteId).routeId(requestRouteId))
				.to("{{spine.toUri}}")
				.waitForResponse(
					forwardExpressAckReceiver(ackRouteId, "{{spine.replyUri}}", "JMSMessageID", "JMSCorrelationId"),
					forwardExpressMessageAggregator(aggregateRouteId, "direct:" + aggregateRouteId, 30000));
		} catch (Exception e) {
			throw Throwables.propagate(e);
		}
	}
	
	/**
	 * HTTP server for incoming messages (async ebXml acks as well as higher level ITK ack messages)
	 * <ul>
	 * <li>Determines the type of incoming message from the declared SOAPAction
	 * <li>Routes the handling to a suitable direct route based on the type
	 */
	private void configureHttpServer() {
		from("{{spine.fromUri}}")
		.id("http-server")
		.choice()
		.when(header("SOAPAction").isEqualTo("urn:oasis:names:tc:ebxml-msg:service/Acknowledgment"))
			.to(EBXML_ACK_RECEIVER_URL)
		.endChoice()
		.when(header("SOAPAction").isEqualTo(simple("urn:nhs:names:services:itk/{{interactionId}}")))
			.to(ITK_ACK_RECEIVER_URL)
		.endChoice()
		.otherwise()
			.log(LoggingLevel.WARN, LOGGER, "Unsupported SOAPAction receieved: ${header.SOAPAction}");
	}
	
	/**
	 * Incoming ebXml ACK receiver route
	 * <ul>
	 * <li>Receives ebXml acks from a direct route (but originally from an HTTP request) [sync]
	 * <li>Extracts the related original message id (for correlation)
	 * <li>Adds the ebXml ack to a JMS topic for later processing (by the process holding the associated transaction open)
	 */
	private void configureEbxmlAckReceiver() {
		from(EBXML_ACK_RECEIVER_URL)
		.id("ebxml-ack-receiver")
		.setHeader("JMSCorrelationID",
			namespaces.xpath("/soap:Envelope/soap:Header/eb:Acknowledgment/eb:RefToMessageId", String.class))
		.setHeader(Exchange.CORRELATION_ID).simple("${header.JMSCorrelationID}")
		.setExchangePattern(ExchangePattern.InOnly)
		.to("{{spine.replyUri}}");
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
