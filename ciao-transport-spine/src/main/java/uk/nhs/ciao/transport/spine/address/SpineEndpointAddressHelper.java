package uk.nhs.ciao.transport.spine.address;

import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import uk.nhs.ciao.logging.CiaoLogMessage;
import uk.nhs.ciao.transport.itk.address.EndpointAddressHelper;

public class SpineEndpointAddressHelper implements EndpointAddressHelper<SpineEndpointAddressIdentifier, SpineEndpointAddress> {
	private final TypeReference<List<SpineEndpointAddress>> addressListTypeReference = new TypeReference<List<SpineEndpointAddress>>() {};
	
	@Override
	public Class<SpineEndpointAddress> getAddressType() {
		return SpineEndpointAddress.class;
	}

	@Override
	public TypeReference<List<SpineEndpointAddress>> getAddressListTypeReference() {
		return addressListTypeReference;
	}

	@Override
	public Collection<SpineEndpointAddressIdentifier> findIdentifiers(final SpineEndpointAddress address) {
		final List<SpineEndpointAddressIdentifier> identifiers = Lists.newArrayList();
		
		final SpineEndpointAddressIdentifier byAsid = identifyByAsid(address);
		if (byAsid != null) {
			identifiers.add(byAsid);
		}
		
		final SpineEndpointAddressIdentifier byODSCode = identifyByODSCode(address);
		if (byODSCode != null) {
			identifiers.add(byODSCode);
		}
		
		return identifiers;
	}

	@Override
	public SpineEndpointAddressIdentifier findBestIdentifier(final SpineEndpointAddress address) {
		SpineEndpointAddressIdentifier bestIdentifier = identifyByAsid(address);
		if (bestIdentifier == null) {
			bestIdentifier = identifyByODSCode(address);
		}
		return bestIdentifier;
	}
	
	private SpineEndpointAddressIdentifier identifyByAsid(final SpineEndpointAddress address) {
		SpineEndpointAddressIdentifier identifier = null;
		
		if (address != null && !Strings.isNullOrEmpty(address.getAsid())) {
			identifier = SpineEndpointAddressIdentifier.byAsid(
					address.getService(), address.getAction(), address.getAsid());
		}
		
		return identifier;
	}
	
	private SpineEndpointAddressIdentifier identifyByODSCode(final SpineEndpointAddress address) {
		SpineEndpointAddressIdentifier identifier = null;
		
		if (address != null && !Strings.isNullOrEmpty(address.getOdsCode())) {
			identifier = SpineEndpointAddressIdentifier.byODSCode(
					address.getService(), address.getAction(), address.getOdsCode());
		}
		
		return identifier;
	}

	@Override
	public SpineEndpointAddress copyAddress(final SpineEndpointAddress address) {
		return address == null ? null : new SpineEndpointAddress(address);
	}

	@Override
	public String getKey(final SpineEndpointAddressIdentifier identifier) {
		return identifier == null ? null : identifier.getKey();
	}

	@Override
	public CiaoLogMessage logId(final SpineEndpointAddressIdentifier identifier,
			final CiaoLogMessage logMsg) {
		identifier.addToLog(logMsg);
		return logMsg;
	}

	@Override
	public CiaoLogMessage logAddress(final SpineEndpointAddress address,
			final CiaoLogMessage logMsg) {
		logMsg.address(address);
		return logMsg;
	}
	
}
