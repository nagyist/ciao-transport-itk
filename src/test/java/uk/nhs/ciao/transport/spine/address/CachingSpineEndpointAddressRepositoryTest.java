package uk.nhs.ciao.transport.spine.address;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unitils.reflectionassert.ReflectionAssert;

/**
 * Unit tests for {@link SpineEndpointAddressRepository}
 */
public class CachingSpineEndpointAddressRepositoryTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(CachingSpineEndpointAddressRepositoryTest.class);
	
	private SpineEndpointAddressRepository backingRepository;
	private CachingSpineEndpointAddressRepository repository;

	private CamelContext camelContext;
	private ProducerTemplate producerTemplate;
	private MockEndpoint endpoint;
	
	private String service;
	private String action;
	private String asid;
	private String odsCode;
	private SpineEndpointAddress address;
	
	@Before
	public void setup() throws Exception {
		camelContext = new DefaultCamelContext();
		producerTemplate = new DefaultProducerTemplate(camelContext);
		
		camelContext.start();
		producerTemplate.start();
		
		final String cacheUri = "mock:cache";
		endpoint = MockEndpoint.resolve(camelContext, cacheUri);		
		backingRepository = Mockito.mock(SpineEndpointAddressRepository.class);
		repository = new CachingSpineEndpointAddressRepository(producerTemplate, cacheUri, backingRepository);
		
		service = "service";
		action = "action";
		asid = "asid";
		odsCode = "ods-code";
		
		address = new SpineEndpointAddress();
		address.setService(service);
		address.setAction(action);
		address.setAsid(asid);
		address.setOdsCode(odsCode);
	}
	
	@After
	public void tearDown() throws Exception {
		if (producerTemplate != null) {
			try {
				producerTemplate.stop();
			} catch (Exception e) {
				LOGGER.warn("Unable to stop producerTemplate", e);
			}
		}
		
		if (camelContext != null) {
			try {
				camelContext.stop();
			} catch (Exception e) {
				LOGGER.warn("Unable to stop camelContext", e);
			}
		}
	}
	
	@Test
	public void testFindByAsidChecksBackingRepository() throws Exception {
		Mockito.when(backingRepository.findByAsid(service, action, asid))
			.thenReturn(address);
		endpoint.expectedBodiesReceived(null, address);
		
		final SpineEndpointAddress actual = repository.findByAsid(service, action, asid);

		ReflectionAssert.assertReflectionEquals(address, actual);
		Mockito.verify(backingRepository).findByAsid(service, action, asid);
		Mockito.verifyNoMoreInteractions(backingRepository);
		endpoint.assertIsSatisfied(0);
	}
	
	@Test
	public void testFindByODSCodeChecksBackingRepository() throws Exception {
		Mockito.when(backingRepository.findByODSCode(service, action, odsCode))
			.thenReturn(address);
		endpoint.expectedBodiesReceived(null, address);
		
		final SpineEndpointAddress actual = repository.findByODSCode(service, action, odsCode);
		
		ReflectionAssert.assertReflectionEquals(address, actual);
		Mockito.verify(backingRepository).findByODSCode(service, action, odsCode);
		Mockito.verifyNoMoreInteractions(backingRepository);
		endpoint.assertIsSatisfied(0);
	}
	
	@Test
	public void testFindByAsidReturnsCachedAddress() throws Exception {
		Mockito.when(backingRepository.findByAsid(service, action, asid))
			.thenReturn(address);
		endpoint.expectedBodiesReceived(null, address);
		
		// Caches the entry
		SpineEndpointAddress actual = repository.findByAsid(service, action, asid);
		ReflectionAssert.assertReflectionEquals(address, actual);
		Mockito.verify(backingRepository).findByAsid(service, action, asid);
		Mockito.verifyNoMoreInteractions(backingRepository);
		
		endpoint.reset();
		endpoint.expectedBodyReceived().constant(null);
		endpoint.expectedHeaderReceived(HazelcastConstants.OBJECT_ID, "service:action/ASID/asid");
		endpoint.whenExchangeReceived(1, new Processor() {
			@Override
			public void process(final Exchange exchange) throws Exception {
				exchange.getOut().setBody(address);
			}
		});
		
		// Uses the entry
		actual = repository.findByAsid(service, action, asid);
		Mockito.verifyNoMoreInteractions(backingRepository);
		endpoint.assertIsSatisfied(0);
	}
	
	@Test
	public void testFindByODSCodeReturnsCachedAddress() throws Exception {
		Mockito.when(backingRepository.findByODSCode(service, action, odsCode))
			.thenReturn(address);
		endpoint.expectedBodiesReceived(null, address);
		
		// Caches the entry
		SpineEndpointAddress actual = repository.findByODSCode(service, action, odsCode);
		ReflectionAssert.assertReflectionEquals(address, actual);
		Mockito.verify(backingRepository).findByODSCode(service, action, odsCode);
		Mockito.verifyNoMoreInteractions(backingRepository);
		
		endpoint.reset();
		endpoint.expectedBodyReceived().constant(null);
		endpoint.expectedHeaderReceived(HazelcastConstants.OBJECT_ID, "service:action/ODS/ods-code");
		endpoint.whenExchangeReceived(1, new Processor() {
			@Override
			public void process(final Exchange exchange) throws Exception {
				exchange.getOut().setBody(address);
			}
		});
		
		// Uses the entry
		actual = repository.findByODSCode(service, action, odsCode);
		Mockito.verifyNoMoreInteractions(backingRepository);
		endpoint.assertIsSatisfied(0);
	}
	
	@Test
	public void testFindByAsidReturnsNullWhenAddressIsNotFound() throws Exception {
		endpoint.expectedBodyReceived().constant(null);
		
		final SpineEndpointAddress actual = repository.findByAsid(service, action, "does-not-exist");
		Assert.assertNull(actual);
		
		endpoint.assertIsSatisfied(0);
	}
	
	@Test
	public void testFindByODSCodeReturnsNullWhenAddressIsNotFound() throws Exception {
		endpoint.expectedBodyReceived().constant(null);
		
		final SpineEndpointAddress actual = repository.findByODSCode(service, action, "does-not-exist");
		Assert.assertNull(actual);
		
		endpoint.assertIsSatisfied(0);
	}
}
