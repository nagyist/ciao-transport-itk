package uk.nhs.ciao.transport.dts.address;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import uk.nhs.ciao.logging.CiaoLogMessage;
import uk.nhs.ciao.transport.itk.address.EndpointAddressHelper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Strings;

public class DTSEndpointAddressHelper implements EndpointAddressHelper<DTSEndpointAddressIdentifier, DTSEndpointAddress> {
	private final TypeReference<List<DTSEndpointAddress>> addressListTypeReference = new TypeReference<List<DTSEndpointAddress>>() {};
	
	@Override
	public Class<DTSEndpointAddress> getAddressType() {
		return DTSEndpointAddress.class;
	}

	@Override
	public TypeReference<List<DTSEndpointAddress>> getAddressListTypeReference() {
		return addressListTypeReference;
	}

	@Override
	public Collection<DTSEndpointAddressIdentifier> findIdentifiers(final DTSEndpointAddress address) {
		final DTSEndpointAddressIdentifier byODSCode = identifyByODSCode(address);
		return byODSCode == null ? Collections.<DTSEndpointAddressIdentifier>emptyList() : Collections.singletonList(byODSCode);
	}
	
	@Override
	public DTSEndpointAddressIdentifier findBestIdentifier(final DTSEndpointAddress address) {
		return identifyByODSCode(address);
	}
	
	private DTSEndpointAddressIdentifier identifyByODSCode(final DTSEndpointAddress address) {
		DTSEndpointAddressIdentifier identifier = null;
		
		if (address != null && !Strings.isNullOrEmpty(address.getOdsCode())) {
			identifier = DTSEndpointAddressIdentifier.byODSCode(
					address.getWorkflowId(), address.getOdsCode());
		}
		
		return identifier;
	}
	
	@Override
	public DTSEndpointAddress copyAddress(final DTSEndpointAddress address) {
		return address == null ? null : new DTSEndpointAddress(address);
	}

	@Override
	public String getKey(final DTSEndpointAddressIdentifier identifier) {
		return identifier == null ? null : identifier.getKey();
	}

	@Override
	public CiaoLogMessage logId(final DTSEndpointAddressIdentifier identifier, final CiaoLogMessage logMsg) {
		identifier.addToLog(logMsg);
		return logMsg;
	}

	@Override
	public CiaoLogMessage logAddress(final DTSEndpointAddress address, final CiaoLogMessage logMsg) {
		logMsg.address(address);
		return logMsg;
	}
}
