package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.Exchange;
import org.apache.camel.component.http4.HttpOperationFailedException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.spring.spi.TransactionErrorHandlerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.nhs.ciao.CIPRoutes;
import uk.nhs.ciao.camel.CamelApplication;
import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.transport.spine.forwardexpress.EbxmlAcknowledgementProcessor;
import uk.nhs.ciao.transport.spine.forwardexpress.ForwardExpressSenderApplication;
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
		// TODO: Complete routes
		
		/*
		 * Documents to send are stored on a JMS queue
		 * Prior to sending the document needs to be converted into
		 * a multi-part request with associated ebXml, hl7, and ITK parts
		 * This multi-part message needs to persist until an ebXml ack is received
		 *  -> a transaction and blocking thread can be used to handle retrys and failover
		 * 
		 * It might be be best to take the original document off the original queue, transform it
		 * into the outgoing message (generating associated tracking / message IDs), and then add
		 * it to a 'sending' queue - this would ensure that an identical outgoing message it sent
		 * during retries (including metadata such as creation time etc)
		 */
		
		from("jms:queue:documents?destination.consumer.prefetchSize=0")
		.errorHandler(new TransactionErrorHandlerBuilder()
			.asyncDelayedRedelivery()
			.maximumRedeliveries(2)
			.backOffMultiplier(2)
			.redeliveryDelay(2000)
			.log(LoggerFactory.getLogger(getClass()))
			.logExhausted(true)
		)
		.transacted("PROPAGATION_NOT_SUPPORTED")
		.unmarshal().json(JsonLibrary.Jackson, TrunkRequestProperties.class)
		.to("freemarker:uk/nhs/ciao/transport/spine/trunk/TrunkRequest.ftl")
		.to("jms:queue:trunk-requests");
		
		from("jms:queue:trunk-requests?destination.consumer.prefetchSize=0")
		.errorHandler(new TransactionErrorHandlerBuilder()
			.asyncDelayedRedelivery()
			.maximumRedeliveries(2)
			.backOffMultiplier(2)
			.redeliveryDelay(2000)
			.log(LoggerFactory.getLogger(ForwardExpressSenderApplication.class))
			.logExhausted(true)
		)
		.transacted("PROPAGATION_NOT_SUPPORTED")
		
		//.setHeader(Exchange.CORRELATION_ID, simple("${bodyAs(String)}"))
		.setHeader(Exchange.FILE_NAME, simple("${header.CamelCorrelationId}/message"))
		.setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
		.setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
		.to("file://./target/docs")				
//		.doTry()
			.to("spine:trunk")
			.process(new EbxmlAcknowledgementProcessor())			
//		.endDoTry()
//		.doCatch(HttpOperationFailedException.class)
//			.process(new HttpErrorHandler())
		;
		
//		try {
//			
//		} catch (CIAOConfigurationException e) {
//			throw new RuntimeException("Unable to build routes from CIAOConfig", e);
//		}
	}
}
