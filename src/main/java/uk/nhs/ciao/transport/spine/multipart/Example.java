package uk.nhs.ciao.transport.spine.multipart;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultProducerTemplate;

import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope;

import com.google.common.collect.Maps;

public class Example {
	public static void main(final String[] args) throws Exception {
		final CamelContext context = new DefaultCamelContext();
		
		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				from("direct:input")
					.convertBodyTo(byte[].class)
					.convertBodyTo(MultipartBody.class)
					.split(simple("${body.parts.get(0)}"))
						.log("Got part: ${header.Content-Type}")
						.to("direct:eback")
					.end();
//					.convertBodyTo(String.class)
//					.log("${body}");
				
				from("direct:eback")
					.convertBodyTo(EbxmlEnvelope.class)
					.log("${body}");
			}
		});
		
		final ProducerTemplate producerTemplate = new DefaultProducerTemplate(context);
		context.start();
		producerTemplate.start();
		final Map<String, Object> headers = Maps.newLinkedHashMap();
		headers.put("Content-Type", "multipart/related; boundary=\"--=_MIME-Boundary\"; type=\"text/xml\"; start=\"<80c4eaee-db6a-4113-99dd-a6bd4d9380e7>\"");
		producerTemplate.sendBodyAndHeaders("direct:input", Example.class.getResourceAsStream("/test.txt"), headers);
	}
}
