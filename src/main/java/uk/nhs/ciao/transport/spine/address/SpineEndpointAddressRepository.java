package uk.nhs.ciao.transport.spine.address;

/**
 * Resolves/looks-up endpoint address details associated with an interaction
 */
public interface SpineEndpointAddressRepository {
	/**
	 * Finds the endpoint address for a destination ODS code and interaction
	 * 
	 * @param service The associated service (typically <code>urn:nhs:names:services:itk</code>)
	 * @param action The associated action
	 * @param odsCode The ODS code of the destination organisation
	 * @return The associated endpoint address, or <code>null</code> if the endpoint could
	 * 			not be found
	 */
	public SpineEndpointAddress findByODSCode(final String service, final String action, final String odsCode) throws Exception;
	
	/**
	 * Finds the endpoint address associated with an ASID
	 * 
	 * @param service The associated service (typically <code>urn:nhs:names:services:itk</code>)
	 * @param action The associated action
	 * @param asid The ID of the destination Accredited System 
	 * @return The associated endpoint address, or <code>null</code> if the endpoint could
	 * 			not be found
	 */
	public SpineEndpointAddress findByAsid(final String service, final String action, final String asid) throws Exception;
}
