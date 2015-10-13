package uk.nhs.ciao.transport.dts.sequence;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link IdSequence}
 */
public class IdSequenceTest {
	private IdSequence idSequence;
	
	@Before
	public void setup() {
		idSequence = mock(IdSequence.class, Mockito.CALLS_REAL_METHODS);
	}
	
	@Test
	public void testInitialId() {
		when(idSequence.incrementCounter()).thenReturn(0L);
		assertEquals("00000001", idSequence.nextId());
	}
	
	@Test
	public void testLastId() {
		when(idSequence.incrementCounter()).thenReturn(99999998L);
		Assert.assertEquals("99999999", idSequence.nextId());
	}
	
	@Test
	public void testWrapAround() {
		when(idSequence.incrementCounter()).thenReturn(99999999L);
		Assert.assertEquals("00000001", idSequence.nextId());
	}
}
