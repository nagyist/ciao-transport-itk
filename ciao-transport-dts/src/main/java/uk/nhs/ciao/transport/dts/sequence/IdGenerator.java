package uk.nhs.ciao.transport.dts.sequence;

/**
 * Generator of unique identifiers
 */
public interface IdGenerator {
	/**
	 * Generates a new unique identifier
	 */
	public String generateId();
}
