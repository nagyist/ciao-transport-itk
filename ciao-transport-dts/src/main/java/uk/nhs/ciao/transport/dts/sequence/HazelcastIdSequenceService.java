package uk.nhs.ciao.transport.dts.sequence;

import java.util.Properties;

import com.hazelcast.core.IMap;
import com.hazelcast.spi.ManagedService;
import com.hazelcast.spi.NodeEngine;
import com.hazelcast.spi.RemoteService;

/**
 * Hazelcast SPI service for {@link HazelcastIdSequence}s.
 * <p>
 * This class should be registered as a service in the hazelcast configuration using the {@link #SERVICE_NAME}.
 * <p>
 * All sequence seed values are maintained in a single distributed map called {@link #MAP_NAME}.
 * This map can be made durable by registering a suitable {@link com.hazelcast.core.MapStore} implementation
 * in the hazelcast configuration.
 */
public class HazelcastIdSequenceService implements ManagedService, RemoteService {
	public static final String SERVICE_NAME = "ciao:dts:idSequenceService";
    public static final String MAP_NAME = "ciao:dts:idSequences";

    private NodeEngine nodeEngine;

    public HazelcastIdSequenceService(final NodeEngine nodeEngine) {
        this.nodeEngine = nodeEngine;
    }

    @Override
    public void init(final NodeEngine nodeEngine, final Properties properties) {
        this.nodeEngine = nodeEngine;
    }

    @Override
    public HazelcastIdSequence createDistributedObject(final Object objectId) {
        final String name = String.valueOf(objectId);
        final IMap<String, Long> sequencesMap = nodeEngine.getHazelcastInstance().getMap(MAP_NAME);
        return new HazelcastIdSequence(name, sequencesMap, name);
    }

	@Override
	public void destroyDistributedObject(final Object objectId) {
		// NOOP
	}

	@Override
	public void reset() {
		// NOOP
	}

	@Override
	public void shutdown() {
		// NOOP
	}

}
