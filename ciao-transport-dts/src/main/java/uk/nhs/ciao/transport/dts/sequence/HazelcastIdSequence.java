package uk.nhs.ciao.transport.dts.sequence;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Preconditions;
import com.hazelcast.core.DistributedObject;
import com.hazelcast.core.IMap;

/**
 * {@link IdSequence} implementation similar to {@link com.hazelcast.core.IdGenerator} however the distributed
 * seed counter is maintained in an IMap rather than an IAtomicLong.
 * <p>
 * The map can be made durable by configuring a MapStore implementation as part of the Hazelcast configuration.
 * <p>
 * Additionally the backing {@link HazelcastIdSequenceService} needs to be added to the Hazelcast configuration
 * so that hazelcast can provide sequence instances (via
 * {@link com.hazelcast.core.HazelcastInstance#getDistributedObject(String, Object)}.
 * 
 * @see HazelcastIdSequenceFactory
 */
public class HazelcastIdSequence extends IdSequence implements DistributedObject {
	private static final int BLOCK_SIZE = 10000;

    private final String name;
    private final IMap<String, Long> sequencesMap;
    private final String entryKey;
	private AtomicInteger residue;
	private AtomicLong local;

    public HazelcastIdSequence(final String name, final IMap<String, Long> sequencesMap, final String entryKey) {
        this.name = name;
        this.sequencesMap = Preconditions.checkNotNull(sequencesMap);
        this.entryKey = Preconditions.checkNotNull(entryKey);
        this.residue = new AtomicInteger(BLOCK_SIZE);
        this.local = new AtomicLong(-1);
    }

    /**
     * Configures the counter using the specified value (if it has not already been initialised)
     */
    public boolean init(final long counter) {
        if (counter <= 0) {
            return false;
        }
        final long step = (counter / BLOCK_SIZE);

        synchronized (this) {
            final boolean init = sequencesMap.putIfAbsent(entryKey, step + 1) == null;
            if (init) {
                local.set(step);
                residue.set((int) (counter % BLOCK_SIZE) + 1);
            }
            return init;
        }
    }

    @Override
    protected long incrementCounter() {
        int value = residue.getAndIncrement();
        if (value >= BLOCK_SIZE) {
            synchronized (this) {
                value = residue.get();
                if (value >= BLOCK_SIZE) {
                    local.set(getAndIncrementCounter());
                    residue.set(0);
                }
                return incrementCounter();
            }
        }
        return local.get() * BLOCK_SIZE + value;
    }
    
    @Override
    public Object getId() {
        return name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getServiceName() {
        return HazelcastIdSequenceService.SERVICE_NAME;
    }

    @Override
    public void destroy() {
        sequencesMap.destroy();
        residue = null;
        local = null;
    }
    
    /**
     * Gets (and claims) the current counter value for this node, and increments the cluster-wide entry in the backing map
     */
    private long getAndIncrementCounter() {
    	boolean updated = false;
    	Long previous = null;
    	
    	// Use a CAS strategy to update the map entry
    	while (!updated) {
    		previous = sequencesMap.get(entryKey);
	    	long next = previous == null ? 1 : previous + 1;
	    	if (previous == null) {
	    		updated = sequencesMap.putIfAbsent(entryKey, next) == null;
	    	} else {
	    		updated = sequencesMap.replace(entryKey, previous, next);
	    	}
    	}
    	
    	return previous == null ? 0 : previous;
    }
}
