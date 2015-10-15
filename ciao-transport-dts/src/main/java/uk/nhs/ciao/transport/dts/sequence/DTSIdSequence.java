package uk.nhs.ciao.transport.dts.sequence;

import java.util.Arrays;

/**
 * {@link IdGenerator} which follows the naming rules from the DTS Client V4 File Interface Spec.
 * <p>
 * The generated IDs are 8 digit, left zero-padded integers, from '00000001' to '99999999'.
 * The sequence then wraps around starting again at '00000001'.
 */
public abstract class DTSIdSequence implements IdGenerator {
	private static final char[] ZEROS = "00000000".toCharArray();
	
	@Override	
	public String generateId() {
		// The sequence runs from 1 -> 99999999, then wraps
		long longValue = (incrementCounter() % 99999999L) + 1;
		
		// String format is left sero-padded to 8 characters
		final char[] chars = Arrays.copyOf(ZEROS, ZEROS.length);
		
		for (int index = chars.length; longValue > 0; longValue = longValue / 10) {
			chars[--index] = (char)('0' + (int)(longValue % 10));
		}
		
		return String.valueOf(chars);
	}
	
	protected abstract long incrementCounter();
}
