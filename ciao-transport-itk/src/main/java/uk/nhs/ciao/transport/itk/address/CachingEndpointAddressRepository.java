package uk.nhs.ciao.transport.itk.address;

import static uk.nhs.ciao.logging.CiaoLogMessage.logMsg;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.impl.DefaultExchange;

import uk.nhs.ciao.logging.CiaoLogMessage;
import uk.nhs.ciao.logging.CiaoLogger;

import com.google.common.base.Preconditions;

/**
 * A {@link EndpointAddressRepository} which pulls addresses from a backing repository and
 * stores the results in a Hazelcast cache.
 * <p>
 * The configuration of the cache (time to live, maximum entries, eviction policy etc) can be specified
 * via the <code>HazelcastInstance</code> provided to Camel's <code>HazelcastComponent</code>.
 * 
 * @see uk.nhs.ciao.transport.itk.route.HazelcastCacheRoute
 */
public class CachingEndpointAddressRepository<ID, A> implements EndpointAddressRepository<ID, A> {
	private static final CiaoLogger LOGGER = CiaoLogger.getLogger(CachingEndpointAddressRepository.class);
	
	private final EndpointAddressHelper<ID, A> helper;
	private final ProducerTemplate producerTemplate;
	private final String cacheUri;
	private final EndpointAddressRepository<ID, A> repository;
	
	public CachingEndpointAddressRepository(final EndpointAddressHelper<ID, A> helper, final ProducerTemplate producerTemplate,
			final String cacheUri, final EndpointAddressRepository<ID, A> repository) {
		this.helper = Preconditions.checkNotNull(helper);
		this.producerTemplate = Preconditions.checkNotNull(producerTemplate);
		this.cacheUri = Preconditions.checkNotNull(cacheUri);
		this.repository = Preconditions.checkNotNull(repository);
	}
	
	@Override
	public A findAddress(final ID identifier) throws Exception {
		LOGGER.info(logId(identifier, logMsg("Searching for SpineEndpointAdddress in cache")));
		
		final String key = helper.getKey(identifier);

		A address = findCachedAddress(key);
		if (address == null) {
			LOGGER.debug(logId(identifier, logMsg(
					"Cached EndpointAdddress could not be found - will query backing repository")));
			address = repository.findAddress(identifier);
			cacheAddress(key, address);
		} else {
			LOGGER.debug(logAddress(address,
					logId(identifier, logMsg("Found cached SpineEndpointAddress"))));
		}
		
		return address;
	}

	private CiaoLogMessage logId(final ID identifier, final CiaoLogMessage logMsg) {
		return helper.logId(identifier, logMsg);
	}
	
	private CiaoLogMessage logAddress(final A address, final CiaoLogMessage logMsg) {
		return helper.logAddress(address, logMsg);
	}
	
	private A findCachedAddress(final String key) {		
		final Exchange exchange = new DefaultExchange(producerTemplate.getCamelContext());
		exchange.setPattern(ExchangePattern.InOut);
		exchange.getIn().setHeader(HazelcastConstants.OPERATION, HazelcastConstants.GET_OPERATION);
		exchange.getIn().setHeader(HazelcastConstants.OBJECT_ID, key);
		
		producerTemplate.send(cacheUri, exchange);
		
		return exchange.getOut().getBody(helper.getAddressType());
	}
	
	private void cacheAddress(final String key, final A address) {
		if (address == null) {
			return;
		}
		
		LOGGER.debug(logAddress(address, logMsg("Adding EndpointAdddress to cache").key(key)));
		
		final Exchange exchange = new DefaultExchange(producerTemplate.getCamelContext());
		exchange.setPattern(ExchangePattern.InOut);
		exchange.getIn().setHeader(HazelcastConstants.OPERATION, HazelcastConstants.PUT_OPERATION);
		exchange.getIn().setHeader(HazelcastConstants.OBJECT_ID, key);
		exchange.getIn().setBody(address);
		
		producerTemplate.send(cacheUri, exchange);
	}}
