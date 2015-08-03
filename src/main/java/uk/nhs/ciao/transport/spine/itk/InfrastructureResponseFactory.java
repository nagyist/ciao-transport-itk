package uk.nhs.ciao.transport.spine.itk;

import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;

import joptsimple.internal.Strings;
import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope.Address;
import uk.nhs.ciao.transport.spine.itk.DistributionEnvelope.ManifestItem;
import uk.nhs.ciao.transport.spine.itk.InfrastructureResponse.ErrorInfo;

public class InfrastructureResponseFactory {
	private Identity identity;
	
	public void setIdentity(final String identity) {
		setIdentity(new Identity(identity));
	}
	
	public void setIdentity(final Identity identity) {
		this.identity = identity;
	}
	
	public DistributionEnvelope createAcknowledgmentWithEnvelope(final TypeConverter typeConverter, final DistributionEnvelope envelope)
				throws TypeConversionException, NoTypeConversionAvailableException {
		final InfrastructureResponse response = createAcknowledgment(envelope);
		return createDistributionEnvelope(typeConverter, envelope, response);
	}
	
	public DistributionEnvelope createDeliveryFailureWithEnvelope(final TypeConverter typeConverter, final DistributionEnvelope envelope)
				throws TypeConversionException, NoTypeConversionAvailableException {
		final InfrastructureResponse response = createDeliveryFailure(envelope);
		return createDistributionEnvelope(typeConverter, envelope, response);
	}
	
	// TODO: This may move to another class
	public DistributionEnvelope createDistributionEnvelope(final DistributionEnvelope envelope, final String payloadBody) {
		final DistributionEnvelope result = new DistributionEnvelope();
		
		// TODO: Check these values
		result.setService("urn:nhs-itk:services:201005:SendInfrastructureAck-v1-0");
		result.getHandlingSpec().setInteration(DistributionEnvelope.INTERACTION_INFRASTRUCTURE_ACK);

		if (identity != null) {
			result.setAuditIdentity(new Identity(identity));
			result.setSenderAddress(new Address(identity.getType(), identity.getUri()));
		}
		
		if (envelope.getSenderAddress() != null) {
			result.addAddress(new Address(envelope.getSenderAddress()));
		}
		
		if (!Strings.isNullOrEmpty(payloadBody)) {
			final ManifestItem manifestItem = new ManifestItem();
			manifestItem.setMimeType("text/xml");
			result.addPayload(manifestItem, payloadBody);
		}
		
		return result;
	}
	
	public InfrastructureResponse createAcknowledgment(final DistributionEnvelope envelope) {
		final InfrastructureResponse response = createBaseResponse(envelope);
		response.setResult(InfrastructureResponse.RESULT_OK);
		return response;
	}
	
	public InfrastructureResponse createDeliveryFailure(final DistributionEnvelope envelope) {
		final InfrastructureResponse response = createBaseResponse(envelope);
		response.setResult(InfrastructureResponse.RESULT_WARNING);
		
		// TODO: Find out which error codes to use
		final ErrorInfo errorInfo = new ErrorInfo();
		errorInfo.setId("urn:tbd");
		errorInfo.setCode("12345");
		errorInfo.setText("Unable to deliver payload");
		response.addError(errorInfo);
		return response;
	}
	
	private DistributionEnvelope createDistributionEnvelope(final TypeConverter typeConverter, final DistributionEnvelope envelope, final InfrastructureResponse response)
			throws TypeConversionException, NoTypeConversionAvailableException {
		final String payloadBody = typeConverter.mandatoryConvertTo(String.class, response);
		return createDistributionEnvelope(envelope, payloadBody);
	}
	
	private InfrastructureResponse createBaseResponse(final DistributionEnvelope envelope) {
		final InfrastructureResponse response = new InfrastructureResponse();
		if (identity != null) {
			response.setReportingIdentity(new Identity(identity));
		}
		
		response.setTimestamp(System.currentTimeMillis());
		response.setServiceRef(envelope.getService());
		response.setTrackingIdRef(envelope.getTrackingId());
		
		return response;
	}
}
