package uk.nhs.ciao.transport.itk.address;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import org.springframework.beans.factory.FactoryBean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;

/**
 * Spring factory bean which creates a {@link MemoryEndpointAddressRepository} populated
 * with addresses loaded from JSON files.
 * <p>
 * The conversion from JSON to concrete address type is handled by Jackson using type information
 * provided by the configured {@link EndpointAddressHelper}.
 * 
 * @param <ID> The type of address id
 * @param <A> The type of addresses handled by this class
 */
public class JsonEndpointAddressLoader<ID, A> implements FactoryBean<MemoryEndpointAddressRepository<ID, A>> {
	private final ObjectMapper objectMapper;
	private final EndpointAddressHelper<ID, A> helper;
    
    private final List<File> files = Lists.newArrayList();
	
	public JsonEndpointAddressLoader(final ObjectMapper objectMapper,
			final EndpointAddressHelper<ID, A> helper) {
		this.objectMapper = Preconditions.checkNotNull(objectMapper);
		this.helper = Preconditions.checkNotNull(helper);
	}
		
	@Override
	public Class<?> getObjectType() {
		return MemoryEndpointAddressRepository.class;
	}
	
	@Override
	public boolean isSingleton() {
		return true;
	}
	
	/**
	 * Sets the list of JSON files to load addresses from
	 */
	public void setFiles(final List<File> files) {
		this.files.clear();
		this.files.addAll(files);
	}
	
	@Override
	public MemoryEndpointAddressRepository<ID, A> getObject() throws Exception {
		final MemoryEndpointAddressRepository<ID, A> repository = new MemoryEndpointAddressRepository<ID, A>(helper);
		
		for (final File file: files) {
			if (file == null) {
				continue;
			}
			
			final InputStream in = new FileInputStream(file);
			try {
				final List<A> addresses = objectMapper.readValue(in, helper.getAddressListTypeReference());
				repository.storeAll(addresses);
			} finally {
				Closeables.closeQuietly(in);
			}
		}
		
		return repository;
	}
}
