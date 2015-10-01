package uk.nhs.ciao.transport.itk.address;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;

/**
 * {@link EndpointAddressRepository} backed by multiple delegate repositories.
 * <p>
 * Addresses are found by trying each delegate in turn until a match is returned.
 */
public class LayeredEndpointAddressRepository<ID, A> implements EndpointAddressRepository<ID, A> {
	private final List<EndpointAddressRepository<ID, A>> repositories;
	
	/**
	 * Constructs a new (empty) repository
	 */
	public LayeredEndpointAddressRepository() {
		this.repositories = Lists.newCopyOnWriteArrayList();
	}
	
	/**
	 * Constructs a new layered repositories backed by the specified delegates
	 * 
	 * @param repositories The delegate repositories to add
	 */
	public LayeredEndpointAddressRepository(final Collection<? extends EndpointAddressRepository<ID, A>> repositories) {
		this.repositories = Lists.newCopyOnWriteArrayList(repositories);
	}
	
	/**
	 * Adds a repository to the end of the chain of delegates
	 * 
	 * @param repository The repository to add
	 */
	public void addRepository(final EndpointAddressRepository<ID, A> repository) {
		if (repository != null) {
			repositories.add(repository);
		}
	}
	
	/**
	 * Removes a repository from the chain of delegates
	 * 
	 * @param repository The repository to remove
	 */
	public void removeRepository(final EndpointAddressRepository<ID, A> repository) {
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
	public A findAddress(final ID identifier) throws Exception {
		for (final EndpointAddressRepository<ID, A> repository: repositories) {
			final A address = repository.findAddress(identifier);
			if (address != null) {
				return address;
			}
		}
		
		return null;
	}
}
