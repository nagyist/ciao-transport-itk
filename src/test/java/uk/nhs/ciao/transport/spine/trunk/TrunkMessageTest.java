package uk.nhs.ciao.transport.spine.trunk;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the generation of outgoing TrunkMessages
 */
public class TrunkMessageTest {

	// Template variables:
	
	// body.mimeBoundary
	
	// body.ebxmlContentId
	// body.ebxmlCorrelationId
	// body.creationTime
	
	// body.hl7ContentId
	// body.hl7RootId
	
	// body.itkContentId
	// body.itkCorrelationId
	// body.itkDocumentId
	// body.itkDocumentBody
	
	// TODO:
	
	// eb:From -> AAA-123456
	// eb:To -> BBB-654321
	
	// HL7 IDs
	
	// itk:address -> X0912
	// itk:auditIdentity -> XZ901
	// itk:senderAddress -> AAA:XZ901:XY7650987
	
	private CamelContext context;
	private ProducerTemplate producerTemplate;
	
	@Before
	public void setup() throws Exception {
		context = new DefaultCamelContext();
		context.addComponent("mock", new MockComponent());
		
		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				 from("direct:a")
	                .to("freemarker:uk/nhs/ciao/transport/spine/trunk/TrunkRequest.ftl")
	                .log("Message body:\n${body}")
	                .to("mock:result");
			}
		});
		
		producerTemplate = new DefaultProducerTemplate(context);
		
		context.start();
		producerTemplate.start();
	}
	
	@After
	public void tearDown() {
		if (producerTemplate != null) {
			try {
				producerTemplate.stop();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		if (context != null) {
			try {
				context.stop();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	@Test
	public void checkTemplate() throws Exception {
		final TrunkRequestProperties body = TrunkRequestProperties.builder()
				.setItkCorrelationId("123567762")
				.setItkDocumentBody("The document content")
				.build();
		
		
		MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
		
		// For now just check that the template executes properly (i.e. there are
		// no unknown variables)
	    mock.expectedMessageCount(1);
	    producerTemplate.sendBody("direct:a", body);
	 
	    mock.assertIsSatisfied();
	}
	
	
	
	/**
	 * Notes from ebMS_v2_0.pdf
	 * <p>
	 * 6.1 Persistent Storage and System Failure
	 * <p>
	 * In order to support the filtering of duplicate messages, a Receiving MSH MUST save the MessageId in
	 * persistent storage. It is also RECOMMENDED the following be kept in persistent storage:
	 * <ul>
	 * <li>the complete message, at least until the information in the message has been passed to the application or other process needing to process it,
	 * <li>the time the message was received, so the information can be used to generate the response to a Message Status Request (see section 7.1.1),
	 * <li>the complete response message. 
	 * </ul>
	 * <p>
	 * 6.5 ebXML Reliable Messaging Protocol - contains details of how sender/receiever should handle
	 * retries, acks and duplicates (based on message id and conversation id)
	 */
}
