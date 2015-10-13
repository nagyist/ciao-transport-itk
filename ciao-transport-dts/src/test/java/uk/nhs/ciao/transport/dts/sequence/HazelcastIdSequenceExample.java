package uk.nhs.ciao.transport.dts.sequence;

import java.util.Random;

import com.hazelcast.config.Config;
import com.hazelcast.config.ServiceConfig;
import com.hazelcast.core.Hazelcast;

public class HazelcastIdSequenceExample {
	public static void main(final String[] args) throws Exception {
		final ServiceConfig serviceConfig = new ServiceConfig();
		serviceConfig.setEnabled(true);
		serviceConfig.setName(HazelcastIdSequenceService.SERVICE_NAME);
		serviceConfig.setClassName(HazelcastIdSequenceService.class.getCanonicalName());
		
		final Config config = new Config();
		config.getServicesConfig().addServiceConfig(serviceConfig);
		
		final HazelcastIdSequenceFactory factory = new HazelcastIdSequenceFactory(
				Hazelcast.newHazelcastInstance(config), "seq");
		final HazelcastIdSequence sequence = factory.getObject();
		
		Thread.sleep(5000);
		
		String next = null;
		final Random random = new Random();
		for (int index = 0; index < 10000000; index++) {
			next = sequence.nextId();
			if (index % 10000 == 0) {
				System.out.println(next);
				Thread.sleep(random.nextInt(10) + 1);
			}
		}
		
		System.out.println(next);
		
		Hazelcast.shutdownAll();
	}
}
