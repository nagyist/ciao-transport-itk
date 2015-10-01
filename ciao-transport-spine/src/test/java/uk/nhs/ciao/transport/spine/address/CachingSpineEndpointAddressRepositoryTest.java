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
import org.unitils.reflectionassert.ReflectionAssert;

import uk.nhs.ciao.camel.CamelUtils;
import uk.nhs.ciao.transport.itk.address.CachingEndpointAddressRepository;
import uk.nhs.ciao.transport.itk.address.EndpointAddressRepository;

/**
 * Unit tests for {@link SpineEndpointAddressRepository}
 */
public class CachingSpineEndpointAddressRepositoryTest {
	private SpineEndpointAddressHelper helper;
	private EndpointAddressRepository<SpineEndpointAddressIdentifier, SpineEndpointAddress> backingRepository;
	private CachingEndpointAddressRepository<SpineEndpointAddressIdentifier, SpineEndpointAddress> repository;

	private CamelContext camelContext;
	private ProducerTemplate producerTemplate;
	private MockEndpoint endpoint;
	
	private String service;
	private String action;
	private String asid;
	private String odsCode;
	private SpineEndpointAddress address;
	
	@SuppressWarnings("unchecked")
	@Before
	public void setup() throws Exception {
		camelContext = new DefaultCamelContext();
		producerTemplate = new DefaultProducerTemplate(camelContext);
		
		camelContext.start();
		producerTemplate.start();
		
		final String cacheUri = "mock:cache";
		endpoint = MockEndpoint.resolve(camelContext, cacheUri);	
		helper = new SpineEndpointAddressHelper();
		backingRepository = Mockito.mock(EndpointAddressRepository.class);
		repository = new CachingEndpointAddressRepository<SpineEndpointAddressIdentifier, SpineEndpointAddress>(
				helper, producerTemplate, cacheUri, backingRepository);
		
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
		CamelUtils.stopQuietly(producerTemplate, camelContext);
	}
	
	@Test
	public void testFindByAsidChecksBackingRepository() throws Exception {
		final SpineEndpointAddressIdentifier id = SpineEndpointAddressIdentifier.byAsid(service, action, asid);
		Mockito.when(backingRepository.findAddress(id))
			.thenReturn(address);
		endpoint.expectedBodiesReceived(null, address);
		
		final SpineEndpointAddress actual = repository.findAddress(id);

		ReflectionAssert.assertReflectionEquals(address, actual);
		Mockito.verify(backingRepository).findAddress(id);
		Mockito.verifyNoMoreInteractions(backingRepository);
		endpoint.assertIsSatisfied(0);
	}
	
	@Test
	public void testFindByODSCodeChecksBackingRepository() throws Exception {
		final SpineEndpointAddressIdentifier id = SpineEndpointAddressIdentifier.byODSCode(service, action, odsCode);
		Mockito.when(backingRepository.findAddress(id))
			.thenReturn(address);
		endpoint.expectedBodiesReceived(null, address);
		
		final SpineEndpointAddress actual = repository.findAddress(id);
		
		ReflectionAssert.assertReflectionEquals(address, actual);
		Mockito.verify(backingRepository).findAddress(id);
		Mockito.verifyNoMoreInteractions(backingRepository);
		endpoint.assertIsSatisfied(0);
	}
	
	@Test
	public void testFindByAsidReturnsCachedAddress() throws Exception {
		final SpineEndpointAddressIdentifier id = SpineEndpointAddressIdentifier.byAsid(service, action, asid);
		Mockito.when(backingRepository.findAddress(id))
			.thenReturn(address);
		endpoint.expectedBodiesReceived(null, address);
		
		// Caches the entry
		SpineEndpointAddress actual = repository.findAddress(id);
		ReflectionAssert.assertReflectionEquals(address, actual);
		Mockito.verify(backingRepository).findAddress(id);
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
		actual = repository.findAddress(id);
		Mockito.verifyNoMoreInteractions(backingRepository);
		endpoint.assertIsSatisfied(0);
	}
	
	@Test
	public void testFindByODSCodeReturnsCachedAddress() throws Exception {
		final SpineEndpointAddressIdentifier id = SpineEndpointAddressIdentifier.byODSCode(service, action, odsCode);
		Mockito.when(backingRepository.findAddress(id))
			.thenReturn(address);
		endpoint.expectedBodiesReceived(null, address);
		
		// Caches the entry
		SpineEndpointAddress actual = repository.findAddress(id);
		ReflectionAssert.assertReflectionEquals(address, actual);
		Mockito.verify(backingRepository).findAddress(id);
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
		actual = repository.findAddress(id);
		Mockito.verifyNoMoreInteractions(backingRepository);
		endpoint.assertIsSatisfied(0);
	}
	
	@Test
	public void testFindByAsidReturnsNullWhenAddressIsNotFound() throws Exception {
		final SpineEndpointAddressIdentifier id = SpineEndpointAddressIdentifier.byAsid(service, action, "does-not-exist");
		final SpineEndpointAddress actual = repository.findAddress(id);
		Assert.assertNull(actual);
		
		endpoint.assertIsSatisfied(0);
	}
	
	@Test
	public void testFindByODSCodeReturnsNullWhenAddressIsNotFound() throws Exception {
		endpoint.expectedBodyReceived().constant(null);
		
		final SpineEndpointAddressIdentifier id = SpineEndpointAddressIdentifier.byODSCode(service, action, "does-not-exist");
		final SpineEndpointAddress actual = repository.findAddress(id);
		Assert.assertNull(actual);
		
		endpoint.assertIsSatisfied(0);
	}
}
