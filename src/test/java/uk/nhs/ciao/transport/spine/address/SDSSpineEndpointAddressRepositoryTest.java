package uk.nhs.ciao.transport.spine.address;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unitils.reflectionassert.ReflectionAssert;

import uk.nhs.ciao.spine.sds.EmbeddedLDAPServer;
import uk.nhs.ciao.spine.sds.SpineDirectoryService;
import uk.nhs.ciao.spine.sds.ldap.DefaultLdapConnection;
import uk.nhs.ciao.spine.sds.ldap.LdapConnection;

/**
 * Unit tests for {@link SDSSpineEndpointAddressRepository}
 * <p>
 * These tests use fixtures provided by the {@link EmbeddedLDAPServer}
 */
public class SDSSpineEndpointAddressRepositoryTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(SDSSpineEndpointAddressRepositoryTest.class);
	private static EmbeddedLDAPServer server;
	
	@BeforeClass
	public static void setupLdapServer() throws Exception {
		server = new EmbeddedLDAPServer();
		server.start();
	}
	
	@AfterClass
	public static void tearDownLdapServer() {
		server.stop();
	}
	
	private SDSSpineEndpointAddressRepository repository;
	
	@Before
	public void setup() {
		final LdapConnection connection = new DefaultLdapConnection(server.getLdapEnvironment());
		final SpineDirectoryService sds = new SpineDirectoryService(connection);
		repository = new SDSSpineEndpointAddressRepository(sds);
	}
	
	@Test
	public void testFindByAsid() throws Exception {
		// Find by asid code will return exactly one AccreditedSystem (ODSCode would return two)
		final SpineEndpointAddress expected = new SpineEndpointAddress();		
		expected.setService("service-1");
		expected.setAction("action-1");
		expected.setAsid("asid-1");
		expected.setOdsCode("ods-code-1");
		expected.setCpaId("cpa-1");
		expected.setMhsPartyKey("party-key-1");
		
		final SpineEndpointAddress actual = findByAsid(expected);
		LOGGER.info("Address lookup - expected={}, actual={}", expected, actual);

		Assert.assertNotNull(actual);
		ReflectionAssert.assertReflectionEquals(expected, actual);
		Assert.assertEquals(expected, actual);
	}
	
	@Test
	public void testFindByODSCode() throws Exception {
		// Find by ods code will return two matches - the most recent is taken (asid-2)
		final SpineEndpointAddress expected = new SpineEndpointAddress();		
		expected.setService("service-1");
		expected.setAction("action-1");
		expected.setAsid("asid-2");
		expected.setOdsCode("ods-code-1");
		expected.setCpaId("cpa-1");
		expected.setMhsPartyKey("party-key-1");
		
		final SpineEndpointAddress actual = findByODSCode(expected);
		LOGGER.info("Address lookup - expected={}, actual={}", expected, actual);

		Assert.assertNotNull(actual);
		ReflectionAssert.assertReflectionEquals(expected, actual);
		Assert.assertEquals(expected, actual);
	}
	
	private SpineEndpointAddress findByAsid(final SpineEndpointAddress address) throws Exception {
		return repository.findByAsid(address.getService(), address.getAction(), address.getAsid());
	}
	
	private SpineEndpointAddress findByODSCode(final SpineEndpointAddress address) throws Exception {
		return repository.findByODSCode(address.getService(), address.getAction(), address.getOdsCode());
	}
}
