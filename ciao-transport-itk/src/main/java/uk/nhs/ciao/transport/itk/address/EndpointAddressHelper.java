package uk.nhs.ciao.transport.itk.address;

import java.util.Collection;
import java.util.List;

import uk.nhs.ciao.logging.CiaoLogMessage;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Helper utilities associated with endpoint addresses of a specified type
 * 
 * @param <ID> The type of address id
 * @param <A> The type of addresses handled by this class
 */
public interface EndpointAddressHelper<ID, A> {
	/**
	 * The type of address handled by this helper
	 */
	Class<A> getAddressType();
	
	/**
	 * The jackson TypeReference for lists of addresses handled by this helper
	 */
	TypeReference<List<A>> getAddressListTypeReference();
	
	/**
	 * Finds values which can be used to identify the specified endpoint address
	 * 
	 * @param address The address to find identifiers for
	 * @return The collection of found identifiers (possibly empty)
	 */
	Collection<ID> findIdentifiers(A address);
	
	/**
	 * Finds the 'best' / most appropriate identifier from a (potentially) partial address.
	 * <p>
	 * The returned identifier should be sufficient to find the full address
	 * details, resulting in a fully enriched object.
	 * 
	 * @param address The address to find an identifier for
	 * @return The best identifier for the address, or null if no such identifier can be determined
	 */
	ID findBestIdentifier(A address);
	
	/**
	 * Creates a copy of the address
	 * 
	 * @param address The address to copy
	 * @return The copy of the address
	 */
	A copyAddress(A address);
	
	/**
	 * Returns the unique string value associated with the identifier
	 * 
	 * @param identifier The identifier to convert to string form
	 * @return The unique string associated with the identifier
	 */
	String getKey(ID identifier);
	
	/**
	 * Adds details of the identifier to the log message
	 * 
	 * @param identifier The identifier to log
	 * @param logMsg The log message to add parameters to
	 * @return <code>logMsg</code> for method chaining
	 */
	CiaoLogMessage logId(ID identifier, CiaoLogMessage logMsg);
	
	/**
	 * Adds details of the address to the log message
	 * 
	 * @param adddress The address to log
	 * @param logMsg The log message to add parameters to
	 * @return <code>logMsg</code> for method chaining
	 */
	CiaoLogMessage logAddress(A address, CiaoLogMessage logMsg);
}
