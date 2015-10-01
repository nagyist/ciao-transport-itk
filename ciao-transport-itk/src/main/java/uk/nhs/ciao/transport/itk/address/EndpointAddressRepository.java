package uk.nhs.ciao.transport.itk.address;

/**
 * Resolves/looks-up endpoint address details associated with an interaction
 * 
 * @param <ID> The type of address identifiers this repository can resolve
 * @param <A> The type of addresses made available by the repository
 */
public interface EndpointAddressRepository<ID, A> {
	/**
	 * Finds the endpoint address for specified identifying value
	 * 
	 * @param identifier A value which identifies the address
	 * @return The associated endpoint address, or <code>null</code> if the endpoint could
	 * 			not be found
	 */
	public A findAddress(final ID identifier) throws Exception;
}
