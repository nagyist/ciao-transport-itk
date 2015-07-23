package uk.nhs.ciao.transport.spine.multipart;

import java.io.InputStreamReader;

import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;

import com.google.common.io.CharStreams;

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
		
		final InputStreamReader reader = new InputStreamReader(Example.class.getResourceAsStream("/test.txt"));
		message.setBody(CharStreams.toString(reader));
		
		final MultipartBody body = MultipartBody.parse(message);
		
		System.out.println(body.toString());
	}
}
