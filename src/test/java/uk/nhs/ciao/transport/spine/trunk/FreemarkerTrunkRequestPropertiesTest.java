package uk.nhs.ciao.transport.spine.trunk;

import java.util.Map;

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

import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.configuration.impl.MemoryCipProperties;
import uk.nhs.ciao.docs.parser.Document;
import uk.nhs.ciao.docs.parser.ParsedDocument;

import com.google.common.collect.Maps;

/**
 * Tests the generation of outgoing trunk request messages using freemarker templates
 */
public class FreemarkerTrunkRequestPropertiesTest {

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
		final Map<String, Object> properties = Maps.newHashMap();
		properties.put("itkCorrelationId", "123567762");
		properties.put("senderPartyId", "AAA-123456");
		properties.put("senderAsid", "866971180017");
		properties.put("senderODSCode", "AAA");
		properties.put("receiverPartyId", "BBB-654321");
		properties.put("receiverAsid", "000000000000");
		properties.put("receiverODSCode", "BBB");
		properties.put("receiverCPAId", "S3024519A3110234");
		properties.put("auditODSCode", "AAA");
		properties.put("interactionId", "COPC_IN000001GB01");
		properties.put("itkProfileId", "urn:nhs-en:profile:eDischargeInpatientDischargeSummary-v1-0");
		properties.put("itkHandlingSpec", "urn:nhs-itk:interaction:copyRecipientAmbulanceServicePatientReport-v1-0");
		
		final Document originalDocument = new Document("data.txt", "The document content".getBytes());
		final ParsedDocument parsedDocument = new ParsedDocument(originalDocument, properties);
		
		final CIAOConfig config = new CIAOConfig(new MemoryCipProperties("cip-name", "cip-version"));
		
		final TrunkRequestProperties body = new TrunkRequestProperties(config, parsedDocument);
		
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
