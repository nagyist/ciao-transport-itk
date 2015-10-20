package uk.nhs.ciao.transport.dts.sequence;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Sets;
import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.ServiceConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

/**
 * Unit tests for {@link HazelcastIdSequence}
 */
public class HazelcastIdSequenceTest {
	private HazelcastInstance hazelcastInstance;
	
	@Before
	public void setup() {
		final ServiceConfig serviceConfig = new ServiceConfig();
		serviceConfig.setEnabled(true);
		serviceConfig.setName(HazelcastIdSequenceService.SERVICE_NAME);
		serviceConfig.setClassName(HazelcastIdSequenceService.class.getCanonicalName());
		
		final Config config = new Config();
		config.getServicesConfig().addServiceConfig(serviceConfig);
		
		final NetworkConfig networkConfig = config.getNetworkConfig();
		networkConfig.getInterfaces().setEnabled(true);
		networkConfig.getInterfaces().setInterfaces(Arrays.asList("127.0.0.1"));
		networkConfig.getJoin().getMulticastConfig().setEnabled(false);
		networkConfig.getJoin().getTcpIpConfig().setMembers(Arrays.asList("127.0.0.1:5701"));
		networkConfig.getJoin().getTcpIpConfig().setEnabled(true);
		
		hazelcastInstance = Hazelcast.newHazelcastInstance(config);
	}
	
	@After
	public void tearDown() {
		Hazelcast.shutdownAll();
	}
	
	@Test
	public void testSequence() throws Exception {
		final HazelcastIdSequence sequence = new HazelcastIdSequenceFactory(hazelcastInstance, "seq").getObject();
		
		final Pattern pattern = Pattern.compile("\\d{8}");
		final Set<String> generated = Sets.newLinkedHashSet();
		for (int count = 0; count < 20; count++) {
			final String id = sequence.generateId();
			Assert.assertFalse("IDs should be unique", generated.contains(id));
			generated.add(id);
			
			Assert.assertTrue("Expected 8 digits", pattern.matcher(id).matches());
		}
	}
	
	@Test
	public void testWraparound() throws Exception {
		final HazelcastIdSequence sequence = new HazelcastIdSequenceFactory(hazelcastInstance, "seq").getObject();
		sequence.init(99999997);
		
		Assert.assertEquals("99999999", sequence.generateId());
		Assert.assertEquals("00000001", sequence.generateId());
	}
}
