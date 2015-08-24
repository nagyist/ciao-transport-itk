package uk.nhs.ciao.transport.spine.address;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.impl.DefaultExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private static final Logger LOGGER = LoggerFactory.getLogger(CachingSpineEndpointAddressRepository.class);
	
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
		final String key = getKey(service, action, "ASID", asid);

		SpineEndpointAddress address = findCachedAddress(key);
		if (address == null) {
			LOGGER.debug("Cached SpineEndpointAdddress could not be found - will query backing repository: key={}", key);
			address = repository.findByAsid(service, action, asid);
			cacheAddress(key, address);
		} else {
			LOGGER.debug("Found cached SpineEndpointAddress: key={}, value={}", key, address);
		}
		
		return address;
	}
	
	@Override
	public SpineEndpointAddress findByODSCode(final String service, final String action,
			final String odsCode) throws Exception {
		final String key = getKey(service, action, "ODS", odsCode);
		
		SpineEndpointAddress address = findCachedAddress(key);
		if (address == null) {
			LOGGER.debug("Cached SpineEndpointAdddress could not be found - will query backing repository: key={}", key);
			address = repository.findByODSCode(service, action, odsCode);
			cacheAddress(key, address);
		} else {
			LOGGER.debug("Found cached SpineEndpointAddress: key={}, value={}", key, address);
		}
		
		return address;
	}
	
	private String getKey(final String service, final String action, final String codeType, final String code) {
		return service + ':' + action + '/' + codeType + '/' + code;
	}
	
	private SpineEndpointAddress findCachedAddress(final String key) {
		LOGGER.debug("Searching for SpineEndpointAdddress in cache: key={}", key);
		
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
		
		LOGGER.debug("Adding SpineEndpointAdddress to cache: key={}, value={}", key, address);
		
		final Exchange exchange = new DefaultExchange(producerTemplate.getCamelContext());
		exchange.setPattern(ExchangePattern.InOut);
		exchange.getIn().setHeader(HazelcastConstants.OPERATION, HazelcastConstants.PUT_OPERATION);
		exchange.getIn().setHeader(HazelcastConstants.OBJECT_ID, key);
		exchange.getIn().setBody(address);
		
		producerTemplate.send(cacheUri, exchange);
	}}
