package uk.nhs.ciao.transport.spine.address;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import uk.nhs.ciao.transport.itk.address.EndpointAddressRepository;
import uk.nhs.ciao.transport.itk.address.LayeredEndpointAddressRepository;

/**
 * Unit tests for {@link LayeredSpineEndpointAddressRepository
 */
public class LayeredSpineEndpointAddressRepositoryTest {
	private LayeredEndpointAddressRepository<SpineEndpointAddressIdentifier, SpineEndpointAddress> repository;
	private EndpointAddressRepository<SpineEndpointAddressIdentifier, SpineEndpointAddress> delegate1;
	private EndpointAddressRepository<SpineEndpointAddressIdentifier, SpineEndpointAddress> delegate2;
	private EndpointAddressRepository<SpineEndpointAddressIdentifier, SpineEndpointAddress> delegate3;

	private String service;
	private String action;
	private String asid;
	private String odsCode;
	
	@SuppressWarnings("unchecked")
	@Before
	public void setup() {
		delegate1 = Mockito.mock(EndpointAddressRepository.class);
		delegate2 = Mockito.mock(EndpointAddressRepository.class);
		delegate3 = Mockito.mock(EndpointAddressRepository.class);
		
		repository = new LayeredEndpointAddressRepository<SpineEndpointAddressIdentifier, SpineEndpointAddress>(
				Arrays.asList(delegate1, delegate2));
		repository.addRepository(delegate3);
		
		service = "service";
		action = "action";
		asid = "asid";
		odsCode = "odsCode";
	}
	
	@Test
	public void findByAsidChecksAllDelegates() throws Exception {
		final SpineEndpointAddressIdentifier id = SpineEndpointAddressIdentifier.byAsid(service, action, asid);
		final SpineEndpointAddress actual = repository.findAddress(id);
		Assert.assertNull(actual);
		Mockito.verify(delegate1).findAddress(id);
		Mockito.verify(delegate2).findAddress(id);
		Mockito.verify(delegate3).findAddress(id);
	}
	
	@Test
	public void findByODSCodeChecksAllDelegates() throws Exception {
		final SpineEndpointAddressIdentifier id = SpineEndpointAddressIdentifier.byODSCode(service, action, odsCode);
		final SpineEndpointAddress actual = repository.findAddress(id);
		Assert.assertNull(actual);
		Mockito.verify(delegate1).findAddress(id);
		Mockito.verify(delegate2).findAddress(id);
		Mockito.verify(delegate3).findAddress(id);
	}
	
	@Test
	public void findByAsidReturnsFirstNonNullResult() throws Exception {
		final SpineEndpointAddressIdentifier id = SpineEndpointAddressIdentifier.byAsid(service, action, asid);
		final SpineEndpointAddress expected = new SpineEndpointAddress();
		Mockito.when(delegate2.findAddress(id)).thenReturn(expected);

		final SpineEndpointAddress actual = repository.findAddress(id);
		Assert.assertSame(expected, actual);
		
		Mockito.verify(delegate1).findAddress(id);
		Mockito.verify(delegate2).findAddress(id);
		Mockito.verifyZeroInteractions(delegate3);
	}
	
	@Test
	public void findByAsidStopsOnFirstException() throws Exception {
		final SpineEndpointAddressIdentifier id = SpineEndpointAddressIdentifier.byAsid(service, action, asid);
		Mockito.when(delegate2.findAddress(id)).thenThrow(new Exception());
		try {
			repository.findAddress(id);
			Assert.fail("Expected exception");
		} catch (Exception e) {
			Mockito.verify(delegate1).findAddress(id);
			Mockito.verify(delegate2).findAddress(id);
			Mockito.verifyZeroInteractions(delegate3);
		}
	}
	
	@Test
	public void findByODSCodeStopsOnFirstException() throws Exception {
		final SpineEndpointAddressIdentifier id = SpineEndpointAddressIdentifier.byODSCode(service, action, odsCode);
		Mockito.when(delegate2.findAddress(id)).thenThrow(new Exception());
		try {
			repository.findAddress(id);
			Assert.fail("Expected exception");
		} catch (Exception e) {
			Mockito.verify(delegate1).findAddress(id);
			Mockito.verify(delegate2).findAddress(id);
			Mockito.verifyZeroInteractions(delegate3);
		}
	}
	
	@Test
	public void findByODSCCodeReturnsFirstNonNullResult() throws Exception {
		final SpineEndpointAddressIdentifier id = SpineEndpointAddressIdentifier.byODSCode(service, action, odsCode);
		final SpineEndpointAddress expected = new SpineEndpointAddress();
		Mockito.when(delegate2.findAddress(id)).thenReturn(expected);

		final SpineEndpointAddress actual = repository.findAddress(id);
		Assert.assertSame(expected, actual);
		
		Mockito.verify(delegate1).findAddress(id);
		Mockito.verify(delegate2).findAddress(id);
		Mockito.verifyZeroInteractions(delegate3);
	}
}
