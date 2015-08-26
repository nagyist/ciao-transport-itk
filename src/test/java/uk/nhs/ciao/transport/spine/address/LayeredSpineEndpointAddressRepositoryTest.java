package uk.nhs.ciao.transport.spine.address;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link LayeredSpineEndpointAddressRepository
 */
public class LayeredSpineEndpointAddressRepositoryTest {
	private LayeredSpineEndpointAddressRepository repository;
	private SpineEndpointAddressRepository delegate1;
	private SpineEndpointAddressRepository delegate2;
	private SpineEndpointAddressRepository delegate3;

	private String service;
	private String action;
	private String asid;
	private String odsCode;
	
	@Before
	public void setup() {
		delegate1 = Mockito.mock(SpineEndpointAddressRepository.class);
		delegate2 = Mockito.mock(SpineEndpointAddressRepository.class);
		delegate3 = Mockito.mock(SpineEndpointAddressRepository.class);
		
		repository = new LayeredSpineEndpointAddressRepository(
				Arrays.asList(delegate1, delegate2));
		repository.addRepository(delegate3);
		
		service = "service";
		action = "action";
		asid = "asid";
		odsCode = "odsCode";
	}
	
	@Test
	public void findByAsidChecksAllDelegates() throws Exception {
		final SpineEndpointAddress actual = repository.findByAsid(service, action, asid);
		Assert.assertNull(actual);
		Mockito.verify(delegate1).findByAsid(service, action, asid);
		Mockito.verify(delegate2).findByAsid(service, action, asid);
		Mockito.verify(delegate3).findByAsid(service, action, asid);
	}
	
	@Test
	public void findByODSCodeChecksAllDelegates() throws Exception {
		final SpineEndpointAddress actual = repository.findByODSCode(service, action, odsCode);
		Assert.assertNull(actual);
		Mockito.verify(delegate1).findByODSCode(service, action, odsCode);
		Mockito.verify(delegate2).findByODSCode(service, action, odsCode);
		Mockito.verify(delegate3).findByODSCode(service, action, odsCode);
	}
	
	@Test
	public void findByAsidReturnsFirstNonNullResult() throws Exception {
		final SpineEndpointAddress expected = new SpineEndpointAddress();
		Mockito.when(delegate2.findByAsid(service, action, asid)).thenReturn(expected);

		final SpineEndpointAddress actual = repository.findByAsid(service, action, asid);
		Assert.assertSame(expected, actual);
		
		Mockito.verify(delegate1).findByAsid(service, action, asid);
		Mockito.verify(delegate2).findByAsid(service, action, asid);
		Mockito.verifyZeroInteractions(delegate3);
	}
	
	@Test
	public void findByAsidStopsOnFirstException() throws Exception {
		Mockito.when(delegate2.findByAsid(service, action, asid)).thenThrow(new Exception());
		try {
			repository.findByAsid(service, action, asid);
			Assert.fail("Expected exception");
		} catch (Exception e) {
			Mockito.verify(delegate1).findByAsid(service, action, asid);
			Mockito.verify(delegate2).findByAsid(service, action, asid);
			Mockito.verifyZeroInteractions(delegate3);
		}
	}
	
	@Test
	public void findByODSCodeStopsOnFirstException() throws Exception {
		Mockito.when(delegate2.findByODSCode(service, action, odsCode)).thenThrow(new Exception());
		try {
			repository.findByODSCode(service, action, odsCode);
			Assert.fail("Expected exception");
		} catch (Exception e) {
			Mockito.verify(delegate1).findByODSCode(service, action, odsCode);
			Mockito.verify(delegate2).findByODSCode(service, action, odsCode);
			Mockito.verifyZeroInteractions(delegate3);
		}
	}
	
	@Test
	public void findByODSCCodeReturnsFirstNonNullResult() throws Exception {
		final SpineEndpointAddress expected = new SpineEndpointAddress();
		Mockito.when(delegate2.findByODSCode(service, action, odsCode)).thenReturn(expected);

		final SpineEndpointAddress actual = repository.findByODSCode(service, action, odsCode);
		Assert.assertSame(expected, actual);
		
		Mockito.verify(delegate1).findByODSCode(service, action, odsCode);
		Mockito.verify(delegate2).findByODSCode(service, action, odsCode);
		Mockito.verifyZeroInteractions(delegate3);
	}
}
