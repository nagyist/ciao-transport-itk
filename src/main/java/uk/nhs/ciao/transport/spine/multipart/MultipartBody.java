package uk.nhs.ciao.transport.spine.multipart;

import java.util.List;
import java.util.UUID;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

/**
 * Represents a Multipart body
 * 
 * @see http://www.w3.org/Protocols/rfc1341/7_2_Multipart.html
 */
public class MultipartBody {
	public static final String CONTENT_ID = "Content-Id";
	public static final String CONTENT_TYPE = "Content-Type";
	public static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
	
	private static final String CRLF = "\r\n";
	private static final String DEFAULT_CONTENT_TRANSFER_ENCODING = "8bit";
	private static final String DEFAULT_BOUNDARY = "--=_MIME-Boundary";
	
	private String boundary;
	private String preamble;
	private final List<Part> parts;
	private String epilogue;
	
	public MultipartBody() {
		boundary = DEFAULT_BOUNDARY;
		preamble = "";
		parts = Lists.newArrayList();
		epilogue = "";
	}
	
	public MultipartBody(final MultipartBody copy) {
		boundary = copy.boundary;
		preamble = copy.preamble;
		parts = Lists.newArrayList();
		epilogue = copy.epilogue;
		
		for (final Part part: copy.parts) {
			addPart(part);
		}
	}
	
	public String getBoundary() {
		return boundary;
	}
	
	public void setBoundary(final String boundary) {
		this.boundary = Preconditions.checkNotNull(boundary);
	}
	
	public String getPreamble() {
		return preamble;
	}
	
	public void setPreamble(final String preamble) {
		this.preamble = Strings.nullToEmpty(preamble);
	}
	
	public List<Part> getParts() {
		return parts;
	}
	
	public void addPart(final Part part) {
		if (part != null) {
			parts.add(part);
		}
	}
	
	public Part addPart(final String contentType, final Object body) {
		if (body == null) {
			return null;
		}
		
		final Part part = new Part();
		part.setContentId(generateContentId());
		part.setContentType(contentType);
		part.setContentTransferEncoding(DEFAULT_CONTENT_TRANSFER_ENCODING);		
		parts.add(part);
		
		return part;
	}
	
	public String getEpilogue() {
		return epilogue;
	}
	
	public void setEpilogue(final String epilogue) {
		this.epilogue = Strings.nullToEmpty(epilogue);
	}
	
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();		
		toString(builder);		
		return builder.toString();
	}
	
	public void toString(final StringBuilder builder) {
		builder.append(preamble);
		
		for (final Part part: parts) {
			// delimiter
			builder.append(CRLF).append("--").append(boundary).append(CRLF);
			
			part.toString(builder);
		}
		
		// close-delimiter
		builder.append(CRLF).append("--").append(boundary).append("--");
		
		builder.append(epilogue);
	}
	
	protected String generateContentId() {
		return UUID.randomUUID().toString();
	}
}
