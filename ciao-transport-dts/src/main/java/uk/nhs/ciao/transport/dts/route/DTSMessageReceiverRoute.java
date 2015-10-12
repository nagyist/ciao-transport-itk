package uk.nhs.ciao.transport.dts.route;

import org.apache.camel.spi.IdempotentRepository;

import uk.nhs.ciao.camel.BaseRouteBuilder;
import uk.nhs.ciao.logging.CiaoCamelLogger;

public class DTSMessageReceiverRoute extends BaseRouteBuilder {
	private static final CiaoCamelLogger LOGGER = CiaoCamelLogger.getLogger(DTSMessageReceiverRoute.class);
	
	private String dtsMessageReceiverUri;
	private String payloadDestinationUri;
	private IdempotentRepository<?> idempotentRepository;
	private IdempotentRepository<?> inProgressRepository;
	
	/**
	 * URI where incoming DTS messages are received from
	 * <p>
	 * input only
	 */
	public void setDTSMessageReceiverUri(final String dtsMessageReceiverUri) {
		this.dtsMessageReceiverUri = dtsMessageReceiverUri;
	}
	
	/**
	 * URI where outgoing payload messages are sent to
	 * <p>
	 * output only
	 */
	public void setPayloadDestinationUri(final String payloadDestinationUri) {
		this.payloadDestinationUri = payloadDestinationUri;
	}
	
	/**
	 * Idempotent repository used to track incoming messages
	 */
	public void setIdempotentRepository(final IdempotentRepository<?> idempotentRepository) {
		this.idempotentRepository = idempotentRepository;
	}
	
	/**
	 * Idempotent repository to use to track in-progress incoming messages
	 */
	public void setInProgressRepository(final IdempotentRepository<?> inProgressRepository) {
		this.inProgressRepository = inProgressRepository;
	}
	
	@Override
	public void configure() throws Exception {
		// TODO:
	}
}
