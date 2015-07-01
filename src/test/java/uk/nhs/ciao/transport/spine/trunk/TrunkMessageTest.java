package uk.nhs.ciao.transport.spine.trunk;

import java.util.Date;
import java.util.UUID;

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
		final TrunkMessageBody body = new TrunkMessageBody("123567762", "The document content");
		
		
		MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
		
		// For now just check that the template executes properly (i.e. there are
		// no unknown variables)
	    mock.expectedMessageCount(1);
	    producerTemplate.sendBody("direct:a", body);
	 
	    mock.assertIsSatisfied();
	}
	
	private static String generateId() {
		return UUID.randomUUID().toString();
	}
	
	// Temporary bean to declare variables required by freemarker
	// Maybe replace with a Map<String, Object> ??
	public static class TrunkMessageBody {
		private final String mimeBoundary = "--=_MIME-Boundary";
		private final String ebxmlContentId = generateId();
		private final String ebxmlCorrelationId;
		private final Date creationTime = new Date();
		private final String hl7ContentId = generateId();
		private final String hl7RootId = generateId();
		private final String itkContentId = generateId();
		private final String itkCorrelationId = generateId();
		private final String itkDocumentId = generateId();
		private final String itkDocumentBody;
		
		public TrunkMessageBody(final String ebxmlCorrelationId, final String itkDocumentBody) {
			this.ebxmlCorrelationId = ebxmlCorrelationId;
			this.itkDocumentBody = itkDocumentBody;
		}

		public String getMimeBoundary() {
			return mimeBoundary;
		}

		public String getEbxmlContentId() {
			return ebxmlContentId;
		}

		public String getEbxmlCorrelationId() {
			return ebxmlCorrelationId;
		}

		public Date getCreationTime() {
			return creationTime;
		}

		public String getHl7ContentId() {
			return hl7ContentId;
		}

		public String getHl7RootId() {
			return hl7RootId;
		}

		public String getItkContentId() {
			return itkContentId;
		}

		public String getItkCorrelationId() {
			return itkCorrelationId;
		}

		public String getItkDocumentId() {
			return itkDocumentId;
		}

		public String getItkDocumentBody() {
			return itkDocumentBody;
		}
	}
}
