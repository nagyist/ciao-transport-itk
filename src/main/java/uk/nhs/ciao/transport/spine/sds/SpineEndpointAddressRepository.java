package uk.nhs.ciao.transport.spine.sds;

/**
 * Resolves/looks-up endpoint address details associated with an interaction
 */
public interface SpineEndpointAddressRepository {
	/**
	 * Finds the endpoint address for a destination ODS code and interaction
	 * 
	 * @param interaction The associated interaction
	 * @param odsCode The ODS code of the destination organisation
	 * @return The associated endpoint address, or <code>null</code> if the endpoint could
	 * 			not be found
	 */
	public SpineEndpointAddress findByODSCode(final String interaction, final String odsCode);
	
	/**
	 * Finds the endpoint address associated with an ASID
	 * 
	 * @param interaction The associated interaction
	 * @param asid The ID of the destination Accredited System 
	 * @return The associated endpoint address, or <code>null</code> if the endpoint could
	 * 			not be found
	 */
	public SpineEndpointAddress findByAsid(final String interaction, final String asid);
}
