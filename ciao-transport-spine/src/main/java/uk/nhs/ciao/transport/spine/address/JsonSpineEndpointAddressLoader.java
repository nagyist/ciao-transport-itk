package uk.nhs.ciao.transport.spine.address;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import org.springframework.beans.factory.FactoryBean;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;

public class JsonSpineEndpointAddressLoader implements FactoryBean<MemorySpineEndpointAddressRepository> {
    private final ObjectMapper objectMapper;
    private final List<File> files = Lists.newArrayList();
	
	public JsonSpineEndpointAddressLoader(final ObjectMapper objectMapper) {
		this.objectMapper = Preconditions.checkNotNull(objectMapper);
	}
	
	@Override
	public Class<?> getObjectType() {
		return MemorySpineEndpointAddressRepository.class;
	}
	
	@Override
	public boolean isSingleton() {
		return true;
	}
	
	public void setFiles(final List<File> files) {
		this.files.clear();
		this.files.addAll(files);
	}
	
	@Override
	public MemorySpineEndpointAddressRepository getObject() throws Exception {
		final MemorySpineEndpointAddressRepository repository = new MemorySpineEndpointAddressRepository();
		
		final TypeReference<List<SpineEndpointAddress>> typeReference = new TypeReference<List<SpineEndpointAddress>>() {};
		
		for (final File file: files) {
			if (file == null) {
				continue;
			}
			
			final InputStream in = new FileInputStream(file);
			try {
				final List<SpineEndpointAddress> addresses = objectMapper.readValue(in, typeReference);
				repository.storeAll(addresses);
			} finally {
				Closeables.closeQuietly(in);
			}
		}
		
		return repository;
	}
}
