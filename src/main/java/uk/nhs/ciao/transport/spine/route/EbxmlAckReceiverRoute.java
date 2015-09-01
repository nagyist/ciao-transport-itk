package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;

import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope;

/**
 * Incoming ebXml ACK receiver route
 * <ul>
 * <li>Receives ebXml acks from a direct route (but originally from an HTTP request) [sync]
 * <li>Extracts the related original message id (for correlation)
 * <li>Adds the ebXml ack to a JMS topic for later processing (by the process holding the associated transaction open)
 */
public class EbxmlAckReceiverRoute extends BaseRouteBuilder {
	private String ebxmlAckReceiverUrl;
	private String ebxmlAckDestinationUrl;
	
	public void setEbxmlAckReceiverUrl(final String ebxmlAckReceiverUrl) {
		this.ebxmlAckReceiverUrl = ebxmlAckReceiverUrl;
	}
	
	public void setEbxmlAckDestinationUrl(final String ebxmlAckDestinationUrl) {
		this.ebxmlAckDestinationUrl = ebxmlAckDestinationUrl;
	}
	
	public void configure() throws Exception {
		from(ebxmlAckReceiverUrl)
			.id("ebxml-ack-receiver")
			.setProperty("envelope").body(EbxmlEnvelope.class)
			.setHeader(Exchange.CORRELATION_ID).simple("${property.envelope.messageData.refToMessageId}", String.class)
			.setExchangePattern(ExchangePattern.InOnly)
			.to(ebxmlAckDestinationUrl)
		.end();
	}
}
