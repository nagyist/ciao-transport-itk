package uk.nhs.ciao.transport.spine.address;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;

/**
 * {@link SpineEndpointAddressRepository} backed by multiple delegate repositories.
 * <p>
 * Addresses are found by trying each delegate in turn until a match is returned.
 */
public class LayeredSpineEndpointAddressRepository implements SpineEndpointAddressRepository {
	private final List<SpineEndpointAddressRepository> repositories;
	
	/**
	 * Constructs a new (empty) repository
	 */
	public LayeredSpineEndpointAddressRepository() {
		this.repositories = Lists.newCopyOnWriteArrayList();
	}
	
	/**
	 * Constructs a new layered repositories backed by the specified delegates
	 * 
	 * @param repositories The delegate repositories to add
	 */
	public LayeredSpineEndpointAddressRepository(final Collection<? extends SpineEndpointAddressRepository> repositories) {
		this.repositories = Lists.newCopyOnWriteArrayList(repositories);
	}
	
	/**
	 * Adds a repository to the end of the chain of delegates
	 * 
	 * @param repository The repository to add
	 */
	public void addRepository(final SpineEndpointAddressRepository repository) {
		if (repository != null) {
			repositories.add(repository);
		}
	}
	
	/**
	 * Removes a repository from the chain of delegates
	 * 
	 * @param repository The repository to remove
	 */
	public void removeRepository(final SpineEndpointAddressRepository repository) {
		if (repository != null) {
			repositories.remove(repository);
		}
	}
	
	/**
	 * {@inheritDoc}
	 * 
	 * @throws Exception When a backing repository throws an exception
	 */
	@Override
	public SpineEndpointAddress findByAsid(final String service, final String action,
			final String asid) throws Exception {
		for (final SpineEndpointAddressRepository repository: repositories) {
			final SpineEndpointAddress address = repository.findByAsid(service, action, asid);
			if (address != null) {
				return address;
			}
		}
		
		return null;
	}
	
	/**
	 * {@inheritDoc}
	 * 
	 * @throws Exception When a backing repository throws an exception
	 */
	@Override
	public SpineEndpointAddress findByODSCode(final String service, final String action,
			final String odsCode) throws Exception {
		for (final SpineEndpointAddressRepository repository: repositories) {
			final SpineEndpointAddress address = repository.findByODSCode(service, action, odsCode);
			if (address != null) {
				return address;
			}
		}
		
		return null;
	}
}
