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
	private String ebxmlAckReceieverUrl;
	private String multipartMessageReceieverUrl;
	
	public void setHttpServerUrl(final String httpServerUrl) {
		this.httpServerUrl = httpServerUrl;
	}
	
	public void setEbxmlAckReceieverUrl(final String ebxmlAckReceieverUrl) {
		this.ebxmlAckReceieverUrl = ebxmlAckReceieverUrl;
	}
	
	public void setMultipartMessageReceieverUrl(final String multipartMessageReceieverUrl) {
		this.multipartMessageReceieverUrl = multipartMessageReceieverUrl;
	}
	
	@Override
	public void configure() throws Exception {
		from(httpServerUrl)
			.id("http-server")
			.choice()
			.when(header("SOAPAction").isEqualTo("urn:oasis:names:tc:ebxml-msg:service/Acknowledgment"))
				.to(ebxmlAckReceieverUrl)
			.endChoice()
			.when(header("SOAPAction").startsWith("urn:nhs:names:services:itk/"))
				.to(multipartMessageReceieverUrl)
			.endChoice()
			.otherwise()
				.log(LoggingLevel.WARN, LOGGER, "Unsupported SOAPAction receieved: ${header.SOAPAction}")
			.endChoice()
		.end();
	}
}
