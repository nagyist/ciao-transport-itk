package uk.nhs.ciao.transport.spine.sds;

/**
 * Resolves/looks-up party details
 */
public interface PartyDetailsResolver {
	/**
	 * Resolves details of a party from the corresponding ODS code
	 * 
	 * @param odsCode The ODS code of the party to resolve
	 * @return The resolved party details, or <code>null</code> if the party could
	 * 			not be resolved
	 */
	public PartyDetails resolveByODSCode(final String odsCode);
}
