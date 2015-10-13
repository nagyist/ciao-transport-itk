package uk.nhs.ciao.transport.dts.sequence;

import org.springframework.beans.factory.FactoryBean;

import com.google.common.base.Preconditions;
import com.hazelcast.core.HazelcastInstance;

/**
 * Spring factory bean to obtain {@link HazelcastIdSequence} instances
 */
public class HazelcastIdSequenceFactory implements FactoryBean<HazelcastIdSequence> {
	private final HazelcastInstance hazelcastInstance;
	private final String name;
	
	public HazelcastIdSequenceFactory(final HazelcastInstance hazelcastInstance, final String name) {
		this.hazelcastInstance = Preconditions.checkNotNull(hazelcastInstance);
		this.name = Preconditions.checkNotNull(name);
	}
	
	@Override
	public boolean isSingleton() {
		return true;
	}
	
	@Override
	public Class<?> getObjectType() {
		return HazelcastIdSequence.class;
	}
	
	@Override
	public HazelcastIdSequence getObject() throws Exception {
		return hazelcastInstance.getDistributedObject(HazelcastIdSequenceService.SERVICE_NAME, name);
	}
}
