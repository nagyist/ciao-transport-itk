package uk.nhs.ciao.transport.dts.sequence;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of {@link DTSIdSequence} backed by an {@link AtomicLong}.
 * <p>
 * Instances of this class are not suitable for clustered deployments.
 */
public class AtomicIdSequence extends DTSIdSequence {
	private final AtomicLong counter;
	
	public AtomicIdSequence() {
		this.counter = new AtomicLong();
	}
	
	public AtomicIdSequence(final long counter) {
		this.counter = new AtomicLong(counter);
	}
	
	/**
     * Configures the counter using the specified value (if it has not already been initialised)
     */
	public boolean init(final long counter) {
        if (counter <= 0) {
            return false;
        }
        return this.counter.compareAndSet(0, counter);
	}
	
	@Override
	protected long incrementCounter() {
		return counter.getAndIncrement();
	}
}
