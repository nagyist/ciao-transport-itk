package uk.nhs.ciao.transport.dts.sequence;

import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Sets;
import com.hazelcast.config.Config;
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
}
