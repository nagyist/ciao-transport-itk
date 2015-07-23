package uk.nhs.ciao.transport.spine.multipart;

import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;

public class Example {
	public static void main(final String[] args) throws Exception {
		Part part = new Part();
		
		part.setContentId("12345");
		part.setContentType("text/plain");
		part.setBody("the body\r\ncontent\r\n");
		
		System.out.println(part);
		
		System.out.println(part.getContentId());
		System.out.println(part.getRawContentId());
		System.out.println(part.getContentType());
		
		System.out.println(ContentType.valueOf("multipart/related; boundary=\"--=_MIME-Boundary\"; type=\"text/xml\"; start=\"<5047539c-6526-426e-b1f1-3dc752175920>\""));
		
		final Message message = new DefaultMessage();
		message.setHeader("Content-Type", "multipart/related; boundary=\"--=_MIME-Boundary\"; type=\"text/xml\"; start=\"<80c4eaee-db6a-4113-99dd-a6bd4d9380e7>\"");
		
		message.setBody(Example.class.getResourceAsStream("/test.txt"));
		
		final MultipartParser parser = new MultipartParser();
		parser.parse(message, message);
		
		System.out.println(message.getBody().toString());
	}
}
