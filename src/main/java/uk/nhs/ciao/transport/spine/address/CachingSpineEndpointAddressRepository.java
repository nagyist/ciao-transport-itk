package uk.nhs.ciao.transport.spine.address;

import static uk.nhs.ciao.logging.CiaoLogMessage.logMsg;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.impl.DefaultExchange;

import uk.nhs.ciao.logging.CiaoLogger;
import uk.nhs.ciao.transport.spine.route.HazelcastCacheRoute;

import com.google.common.base.Preconditions;

/**
 * A {@link SpineEndpointAddressRepository} which pulls addresses from a backing repository and
 * stores the results in a Hazelcast cache.
 * <p>
 * The configuration of the cache (time to live, maximum entries, eviction policy etc) can be specified
 * via the <code>HazelcastInstance</code> provided to Camel's <code>HazelcastComponent</code>.
 * 
 * @see HazelcastCacheRoute
 */
public class CachingSpineEndpointAddressRepository implements SpineEndpointAddressRepository {
	private static final CiaoLogger LOGGER = CiaoLogger.getLogger(CachingSpineEndpointAddressRepository.class);
	
	private final ProducerTemplate producerTemplate;
	private final String cacheUri;
	private final SpineEndpointAddressRepository repository;
	
	public CachingSpineEndpointAddressRepository(final ProducerTemplate producerTemplate, final String cacheUri, 
			final SpineEndpointAddressRepository repository) {
		this.producerTemplate = Preconditions.checkNotNull(producerTemplate);
		this.cacheUri = Preconditions.checkNotNull(cacheUri);
		this.repository = Preconditions.checkNotNull(repository);
	}
	
	@Override
	public SpineEndpointAddress findByAsid(final String service, final String action,
			final String asid) throws Exception {
		LOGGER.info(logMsg("Searching for SpineEndpointAdddress in cache")
				.service(service).action(action).asid(asid));
		
		final String key = getKey(service, action, "ASID", asid);

		SpineEndpointAddress address = findCachedAddress(key);
		if (address == null) {
			LOGGER.debug(logMsg("Cached SpineEndpointAdddress could not be found - will query backing repository")
					.service(service).action(action).asid(asid));
			address = repository.findByAsid(service, action, asid);
			cacheAddress(key, address);
		} else {
			LOGGER.debug(logMsg("Found cached SpineEndpointAddress")
					.service(service).action(action).asid(asid).address(address));
		}
		
		return address;
	}
	
	@Override
	public SpineEndpointAddress findByODSCode(final String service, final String action,
			final String odsCode) throws Exception {
		LOGGER.info(logMsg("Searching for SpineEndpointAdddress in cache")
				.service(service).action(action).odsCode(odsCode));
		
		final String key = getKey(service, action, "ODS", odsCode);
		
		SpineEndpointAddress address = findCachedAddress(key);
		if (address == null) {
			LOGGER.debug(logMsg("Cached SpineEndpointAdddress could not be found - will query backing repository")
					.service(service).action(action).odsCode(odsCode));
			address = repository.findByODSCode(service, action, odsCode);
			cacheAddress(key, address);
		} else {
			LOGGER.debug(logMsg("Found cached SpineEndpointAddress")
					.service(service).action(action).odsCode(odsCode).address(address));
		}
		
		return address;
	}
	
	private String getKey(final String service, final String action, final String codeType, final String code) {
		return service + ':' + action + '/' + codeType + '/' + code;
	}
	
	private SpineEndpointAddress findCachedAddress(final String key) {		
		final Exchange exchange = new DefaultExchange(producerTemplate.getCamelContext());
		exchange.setPattern(ExchangePattern.InOut);
		exchange.getIn().setHeader(HazelcastConstants.OPERATION, HazelcastConstants.GET_OPERATION);
		exchange.getIn().setHeader(HazelcastConstants.OBJECT_ID, key);
		
		producerTemplate.send(cacheUri, exchange);
		
		return exchange.getOut().getBody(SpineEndpointAddress.class);
	}
	
	private void cacheAddress(final String key, final SpineEndpointAddress address) {
		if (address == null) {
			return;
		}
		
		LOGGER.debug(logMsg("Adding SpineEndpointAdddress to cache").key(key).address(address));
		
		final Exchange exchange = new DefaultExchange(producerTemplate.getCamelContext());
		exchange.setPattern(ExchangePattern.InOut);
		exchange.getIn().setHeader(HazelcastConstants.OPERATION, HazelcastConstants.PUT_OPERATION);
		exchange.getIn().setHeader(HazelcastConstants.OBJECT_ID, key);
		exchange.getIn().setBody(address);
		
		producerTemplate.send(cacheUri, exchange);
	}}
