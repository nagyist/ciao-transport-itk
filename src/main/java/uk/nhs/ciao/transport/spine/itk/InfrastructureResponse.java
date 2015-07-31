package uk.nhs.ciao.transport.spine.itk;

import java.util.List;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;

public class InfrastructureResponse {
	public static final String RESULT_OK = "OK";
	public static final String RESULT_FAILURE = "Failure";
	public static final String RESULT_WARNING = "Warning";
	
	private String result; // required
	private String timestamp;
	private String serviceRef; // required
	private String trackingIdRef; // required
	
	private Identity reportingIdentity;
	private List<ErrorInfo> errors = Lists.newArrayList();
	
	public String getResult() {
		return result;
	}
	
	public void setResult(final String result) {
		this.result = result;
	}
	
	public boolean isAck() {
		return RESULT_OK.equalsIgnoreCase(result);
	}
	
	public boolean isNack() {
		return !isAck();
	}
	
	public String getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(final String timestamp) {
		this.timestamp = timestamp;
	}
	
	public String getServiceRef() {
		return serviceRef;
	}
	
	public void setServiceRef(final String serviceRef) {
		this.serviceRef = serviceRef;
	}
	
	public String getTrackingIdRef() {
		return trackingIdRef;
	}
	
	public void setTrackingIdRef(final String trackingIdRef) {
		this.trackingIdRef = trackingIdRef;
	}
	
	public Identity getReportingIdentity() {
		return reportingIdentity;
	}
	
	public void setReportingIdentity(final Identity reportingIdentity) {
		this.reportingIdentity = reportingIdentity;
	}
	
	public List<ErrorInfo> getErrors() {
		return errors;
	}
	
	public void addError(final ErrorInfo error) {
		if (error != null) {
			errors.add(error);
		}
	}
	
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
			.add("result", result)
			.add("timestamp", timestamp)
			.add("serviceRef", serviceRef)
			.add("trackingIdRef", trackingIdRef)
			.add("reportingIdentity", reportingIdentity)
			.add("errors", errors)
			.toString();
	}
	
	public static class ErrorInfo {
		private static final String DEFAULT_CODE_SYSTEM = "2.16.840.1.113883.2.1.3.2.4.17.268";
		
		private String id;
		private String code;
		private String codeSystem;
		private String text;
		private String diagnosticText;
		
		public String getId() {
			return id;
		}
		
		public void setId(final String id) {
			this.id = id;
		}
		
		public String getCode() {
			return code;
		}
		
		public void setCode(final String code) {
			this.code = code;
		}
		
		public String getCodeSystem() {
			return codeSystem;
		}
		
		public void setCodeSystem(final String codeSystem) {
			this.codeSystem = codeSystem;
		}
		
		public boolean isDefaultCodeSystem() {
			return DEFAULT_CODE_SYSTEM.equals(codeSystem);
		}
		
		public String getText() {
			return text;
		}
		
		public void setText(final String text) {
			this.text = text;
		}
		
		public String getDiagnosticText() {
			return diagnosticText;
		}
		
		public void setDiagnosticText(final String diagnosticText) {
			this.diagnosticText = diagnosticText;
		}
		
		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this)
				.add("id", id)
				.add("code", code)
				.add("codeSystem", codeSystem)
				.add("text", text)
				.add("diagnosticText", diagnosticText)
				.toString();
		}
	}
	
}
