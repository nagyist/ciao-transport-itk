package uk.nhs.ciao.transport.spine.route;

import static uk.nhs.ciao.logging.CiaoCamelLogMessage.camelLogMsg;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Headers;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.util.toolbox.AggregationStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.nhs.ciao.camel.BaseRouteBuilder;
import uk.nhs.ciao.logging.CiaoCamelLogMessage;
import uk.nhs.ciao.logging.CiaoCamelLogger;

/**
 * HTTP server for incoming messages
 * <p>
 * Receives async ebXml acks as well as higher level ITK messages (encoded as multipart messages)
 * <ul>
 * <li>Determines the type of incoming message from the declared SOAPAction
 * <li>Routes the message to a suitable route for handling (based on the type)
 */
public class HttpServerRoute extends BaseRouteBuilder {
	private static final CiaoCamelLogger LOGGER = CiaoCamelLogger.getLogger(HttpServerRoute.class);
	
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
			
			.process(LOGGER.debug(camelLogMsg("Received incoming HTTP request")
				.uri(header(Exchange.HTTP_URI))
				.soapAction(header("SOAPAction"))))
			
			.bean(new HeaderNormaliser())
			.choice()
				.when(PredicateBuilder.or(
						header("SOAPAction").contains("urn:oasis:names:tc:ebxml-msg:service/Acknowledgment"),
						header("SOAPAction").contains("urn:oasis:names:tc:ebxml-msg:service/MessageError")))
						
					.process(LOGGER.info(camelLogMsg("Received incoming spine ebXml message over HTTP")
						.uri(header(Exchange.HTTP_URI))
						.soapAction(header("SOAPAction"))
						.eventName("incoming-spine-ebxml-message")))
						
					.multicast(AggregationStrategies.useOriginal())
						.to(ebxmlAckReceiverUrl)
					.end()
					.removeHeaders("*")
					.setHeader(Exchange.HTTP_RESPONSE_CODE).constant("200")
					.setBody().constant("")
				.endChoice()
				.when(header("SOAPAction").contains("urn:nhs:names:services:itk/"))
				
					.process(LOGGER.info(camelLogMsg("Received incoming spine multipart message (ITK) over HTTP")
						.uri(header(Exchange.HTTP_URI))
						.soapAction(header("SOAPAction"))
						.eventName("incoming-spine-multipart-message")))
				
					.to(multipartMessageReceiverUrl)
				.endChoice()
				.otherwise()
					.process(LOGGER.warn(camelLogMsg("Received unsupported HTTP SOAPAction")
						.uri(header(Exchange.HTTP_URI))
						.soapAction(header("SOAPAction"))
						.eventName("unsupported-soap-action")))
					
					.setBody().simple("Unsupported SOAPAction: ${header.SOAPAction}")
					.removeHeaders("*")
					.setHeader(Exchange.HTTP_RESPONSE_CODE).constant("400")
					.setHeader(Exchange.CONTENT_TYPE).constant("text/plain")
				.endChoice()
			.end()
		.end();
	}
	
	/**
	 * Normalises incoming HTTP header value
	 * <p>
	 * In particular, headers can optionally be enclosed in double-quotes. To simplify
	 * pattern matching on SOAPHeader - these quotes are removed
	 */
	public static class HeaderNormaliser {
		public void normaliseHeaders(@Headers final Map<String, Object> headers) {
			stripEnclosingQuotes(headers, "SOAPAction");
		}
		
		private void stripEnclosingQuotes(final Map<String, Object> headers, final String name) {
			final Object value = headers.get(name);
			if (value instanceof String) {
				String string = (String)value;
				if (string.length() > 1 && string.charAt(0) == '"' && string.charAt(string.length() - 1) == '"') {
					string = string.substring(1, string.length() - 1);
					headers.put(name, string);
				}
			}
		}
	}
}
