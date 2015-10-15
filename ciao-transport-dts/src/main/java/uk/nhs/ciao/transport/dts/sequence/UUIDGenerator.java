package uk.nhs.ciao.transport.dts.sequence;

import java.util.UUID;

/**
 * {@link IdGenerator} implementation backed by randomly generated UUIDs
 */
public class UUIDGenerator implements IdGenerator {
	@Override
	public String generateId() {
		return UUID.randomUUID().toString();
	}
}
