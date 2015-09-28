package uk.nhs.ciao.transport.spine.address;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link MemorySpineEndpointAddressRepository}
 */
public class MemorySpineEndpointAddressRepositoryTest {
	private MemorySpineEndpointAddressRepository repository;
	
	private SpineEndpointAddress address1;
	private SpineEndpointAddress address2;
	
	@Before
	public void setup() {
		repository = new MemorySpineEndpointAddressRepository();
		address1 = new SpineEndpointAddress();
		address1.setService("service");
		address1.setAction("action");
		address1.setAsid("code");
		address1.setOdsCode("another-code");
		
		address2 = new SpineEndpointAddress();
		address2.setService("service");
		address2.setAction("action");
		address2.setOdsCode("code");
		address2.setAsid("another-code");
		
		repository.storeAll(Arrays.asList(address1, address2));
	}
	
	@Test
	public void testFindByAsid() {
		final SpineEndpointAddress expected = address1;
		final SpineEndpointAddress actual = repository.findByAsid("service", "action", "code");
		Assert.assertEquals(expected, actual);
	}
	
	@Test
	public void testFindByODSCode() {
		final SpineEndpointAddress expected = address2;
		final SpineEndpointAddress actual = repository.findByODSCode("service", "action", "code");
		Assert.assertEquals(expected, actual);
	}
}
