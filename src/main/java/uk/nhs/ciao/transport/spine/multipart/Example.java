package uk.nhs.ciao.transport.spine.multipart;

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
	}
}
