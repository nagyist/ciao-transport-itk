package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.LoggingLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP server for incoming messages
 * <p>
 * Receives async ebXml acks as well as higher level ITK messages (encoded as multipart messages)
 * <ul>
 * <li>Determines the type of incoming message from the declared SOAPAction
 * <li>Routes the message to a suitable route for handling (based on the type)
 */
public class HttpServerRoute extends BaseRouteBuilder {
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerRoute.class);
	
	private String httpServerUrl;
	private String ebxmlAckReceiverUrl;
	private String multipartMessageReceiverUrl;
	
	public void setHttpServerUrl(final String httpServerUrl) {
		this.httpServerUrl = httpServerUrl;
	}
	
	public void setEbxmlAckReceiverUrl(final String ebxmlAckReceiverUrl) {
		this.ebxmlAckReceiverUrl = ebxmlAckReceiverUrl;
	}
	
	public void setMultipartMessageReceiverUrl(final String multipartMessageReceiverUrl) {
		this.multipartMessageReceiverUrl = multipartMessageReceiverUrl;
	}
	
	@Override
	public void configure() throws Exception {
		from(httpServerUrl)
			.id("http-server")
			.choice()
			.when(header("SOAPAction").isEqualTo("urn:oasis:names:tc:ebxml-msg:service/Acknowledgment"))
				.to(ebxmlAckReceiverUrl)
			.endChoice()
			.when(header("SOAPAction").startsWith("urn:nhs:names:services:itk/"))
				.to(multipartMessageReceiverUrl)
			.endChoice()
			.otherwise()
				.log(LoggingLevel.WARN, LOGGER, "Unsupported SOAPAction receieved: ${header.SOAPAction}")
			.endChoice()
		.end();
	}
}
