package uk.nhs.ciao.transport.dts.route;

import java.util.regex.Pattern;

import org.apache.camel.spi.IdempotentRepository;

import com.google.common.base.Strings;

import uk.nhs.ciao.camel.BaseRouteBuilder;
import uk.nhs.ciao.camel.URIBuilder;
import uk.nhs.ciao.logging.CiaoCamelLogger;

public class DTSMessageReceiverRoute extends BaseRouteBuilder {
	private static final CiaoCamelLogger LOGGER = CiaoCamelLogger.getLogger(DTSMessageReceiverRoute.class);
	
	private String dtsMessageReceiverUri;
	private String payloadDestinationUri;
	private IdempotentRepository<?> idempotentRepository;
	private IdempotentRepository<?> inProgressRepository;
	
	// optional properties
	private String dtsFilePrefix = "";
	
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
	
	/**
	 * A prefix to be added to DTS file names before they are sent to the client.
	 * <p>
	 * The DTS documentation recommends <code>${siteid}${APP}</code>:
	 * <ul>
	 * <li>siteId: will uniquely identify the client site. The DTS Client userid should be used for this value.</li>
	 * <li>APP: an optional value used to allow different applications on the platform to use the same client to transfer files.</li>
	 * </ul>
	 */
	public void setDTSFilePrefix(final String dtsFilePrefix) {
		this.dtsFilePrefix = Strings.nullToEmpty(dtsFilePrefix);
	}
	
	@Override
	public void configure() throws Exception {
		// Additional configuration parameters are appended to the endpoint URI
		final URIBuilder uri = new URIBuilder(dtsMessageReceiverUri);
		
		// only process files intended for this application
		if (!Strings.isNullOrEmpty(dtsFilePrefix)) {
			uri.set("include", Pattern.quote(dtsFilePrefix) + ".+\\.ctl");
		}
		
		// only process each file once
		uri.set("idempotent", true)
			.set("idempotentRepository", idempotentRepository) // TODO: This can't work - needs ID!
			.set("inProgressRepository", inProgressRepository) // TODO: This can't work - needs ID!
			.set("readLock", "idempotent");
		
		// delete after processing
		// TODO: Should these be moved to another directory instead of delete?
		uri.set("delete", true);
		
		// Cleanup the repository after processing / delete
		// The readLockRemoveOnCommit option is not available in this version of camel
		// The backing repository will need additional configuration to expunge old entries
		// If using hazelcast the backing map can be configured with a max time to live for each key
//		endpointParams.put("readLockRemoveOnCommit", true);
		
		from(uri.toString())
			
		.end();
	}
}
