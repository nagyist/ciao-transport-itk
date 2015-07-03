package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.Exchange;
import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.component.http4.HttpOperationFailedException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.spring.spi.TransactionErrorHandlerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.nhs.ciao.CIPRoutes;
import uk.nhs.ciao.camel.CamelApplication;
import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.transport.spine.forwardexpress.EbxmlAcknowledgementProcessor;
import uk.nhs.ciao.transport.spine.trunk.TrunkRequestProperties;

/**
 * Configures multiple camel CDA builder routes determined by properties specified
 * in the applications registered {@link CIAOConfig}.
 */
@SuppressWarnings("unused")
public class SpineTransportRoutes extends CIPRoutes {
	private static final Logger LOGGER = LoggerFactory.getLogger(SpineTransportRoutes.class);
	
	/**
	 * The root property 
	 */
	public static final String ROOT_PROPERTY = "spineTransportRoutes";
	
	/**
	 * Creates multiple document parser routes
	 * 
	 * @throws RuntimeException If required CIAO-config properties are missing
	 */
	@Override
	public void configure() {
		super.configure();
		
		final CIAOConfig config = CamelApplication.getConfig(getContext());

		// Document input route
		// * transforms the ParsedDocument json into a multi-part trunk request message
		// * Adds the trunk request message onto a JMS queue for later processing (by any running process)
		from("jms:queue:documents?destination.consumer.prefetchSize=0")
		.errorHandler(new TransactionErrorHandlerBuilder()
			.asyncDelayedRedelivery()
			.maximumRedeliveries(2)
			.backOffMultiplier(2)
			.redeliveryDelay(2000)
			.log(LOGGER)
			.logExhausted(true)
		)
		.transacted("PROPAGATION_NOT_SUPPORTED")
		.unmarshal().json(JsonLibrary.Jackson, TrunkRequestProperties.class)
		.setHeader("SOAPAction").constant("urn:nhs:names:services:itk/COPC_IN000001GB01")
		.setHeader(Exchange.CONTENT_TYPE).simple("multipart/related; boundary=\"${body.mimeBoundary}\"; type=\"text/xml\"; start=\"<${body.ebxmlContentId}>\"")
		.to("freemarker:uk/nhs/ciao/transport/spine/trunk/TrunkRequest.ftl")
		.to("jms:queue:trunk-requests");
		
		
		 // Outgoing trunk message queue
		 // * sends a multi-part trunk request message over the spine
		 // * blocks until an async ebXml ack is received off a configured JMS topic or a timeout occurs
		 // * marks message as success, retry or failure based on the ACK content
		from("jms:queue:trunk-requests?destination.consumer.prefetchSize=0")
		.id("trunk-requests")
		.errorHandler(new TransactionErrorHandlerBuilder()
			.asyncDelayedRedelivery()
			.maximumRedeliveries(2)
			.backOffMultiplier(2)
			.redeliveryDelay(2000)
			.log(LOGGER)
			.logExhausted(true)
		)
		.transacted("PROPAGATION_NOT_SUPPORTED")
		
		.setHeader(Exchange.FILE_NAME, simple("${header.CamelCorrelationId}/message"))
		.setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
		.to("file://./target/docs")				
//		.doTry()
			.to("spine:trunk")
			.process(new EbxmlAcknowledgementProcessor())
//		.endDoTry()
//		.doCatch(HttpOperationFailedException.class)
//			.process(new HttpErrorHandler())
		;
		
		// Incoming ebXml ACK receiver route
		// * Receives ebXml acks (over HTTP)
		// * Extracts the related original message id (for correlation)
		// * Adds the ebXml ack to a JMS topic for later processing (by the process holding the associated transaction open)
		final Namespaces ns = new Namespaces("soap", "http://schemas.xmlsoap.org/soap/envelope/");
		ns.add("eb", "http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd");
		
		from("{{spine.fromUri}}")
			.setHeader("JMSCorrelationID",
				ns.xpath("/soap:Envelope/soap:Header/eb:Acknowledgment/eb:RefToMessageId", String.class))
			.setHeader(Exchange.CORRELATION_ID).simple("${header.JMSCorrelationID}")
			.to("{{spine.replyUri}}");
	}
}
