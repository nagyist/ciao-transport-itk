package uk.nhs.ciao.transport.spine.address;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import uk.nhs.ciao.transport.itk.address.MemoryEndpointAddressRepository;

/**
 * Unit tests for {@link MemorySpineEndpointAddressRepository}
 */
public class MemorySpineEndpointAddressRepositoryTest {
	private MemoryEndpointAddressRepository<SpineEndpointAddressIdentifier, SpineEndpointAddress> repository;
	
	private SpineEndpointAddress address1;
	private SpineEndpointAddress address2;
	
	@Before
	public void setup() {
		final SpineEndpointAddressHelper helper = new SpineEndpointAddressHelper();
		repository = new MemoryEndpointAddressRepository<SpineEndpointAddressIdentifier, SpineEndpointAddress>(helper);
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
		final SpineEndpointAddressIdentifier id = SpineEndpointAddressIdentifier.byAsid("service", "action", "code");
		final SpineEndpointAddress actual = repository.findAddress(id);
		Assert.assertEquals(expected, actual);
	}
	
	@Test
	public void testFindByODSCode() {
		final SpineEndpointAddress expected = address2;
		final SpineEndpointAddressIdentifier id = SpineEndpointAddressIdentifier.byODSCode("service", "action", "code");
		final SpineEndpointAddress actual = repository.findAddress(id);
		Assert.assertEquals(expected, actual);
	}
}
