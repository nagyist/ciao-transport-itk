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
		
		/**
		 * Uses:
		 * HTTP Content-Type header - boundary parameter
		 * Splits individual Multipart MIME sections in request body
		 * 
		 * A static value can be chosen - but it should be a value
		 * with a small chance of appearing in actual multi-part
		 * content - otherwise the document may fail to parse
		 * properly
		 */
		private final String mimeBoundary = "--=_MIME-Boundary";
		
		// ebxml variables
		
		/**
		 * Uses:
		 * HTTP Content-Type header - start parameter
		 * Multipart inline header - Content-ID for ebxml part
		 */
		private final String ebxmlContentId = generateId();
		
		/**
		 * Uses (Request):
		 * eb:ConversationId
		 * eb:MessageId
		 * 
		 * Uses (ebxml ACK):
		 * eb:ConversationId
		 * eb:RefToMessageId
		 * (ACK itself has it's own eb:MessageId)
		 * <p>
		 * This might be better split into two variables conversationId and messageId - for
		 * the initial request they can have the same value (for convenience). The subsequent
		 * ack will refer to conversationId but will have it's own messageId value.
		 * <p>
		 * The ack (probably) ends the conversation from the ebXml perspective. Subsequent
		 * ITK ack messages 'may' be considered new conversations (from the ebXml perspective).
		 * They will contain references to the original ITK trackingId - but may not have the
		 * same ebXml conversationId. It might be safer not to rely on the conversationID being
		 * the same across the whole (ITK) interaction - in any case when replying to the ITK
		 * acks (with ebXml acks) the incoming ebXml values should be echoed back in the ebXml
		 * ack. At a minimum, this requires storing the incoming eb:MessageId in persistent
		 * storage to support duplicate detection (as described in the ebXml spec).
		 */
		private final String ebxmlCorrelationId;
		
		/**
		 * Uses:
		 * eb:Timestamp (in MessageData)
		 * HL7 - creationTime
		 * 
		 * TODO: Should this change if message is resent? Do they always refer to this same time?
		 */
		private final Date creationTime = new Date();
		
		// HL7 variables
		
		/**
		 * Uses:
		 * Multipart inline header - Content-ID for HL7 part
		 * Manifest reference in ebxml part
		 */
		private final String hl7ContentId = generateId();
		
		/**
		 * Uses:
		 * HL7 root ID - no other references
		 */
		private final String hl7RootId = generateId();
		
		// ITK variables
		
		/**
		 * Uses:
		 * Multipart inline header - Content-ID for ITK part
		 * Manifest reference in ebxml part
		 */
		private final String itkContentId = generateId();
		
		/**
		 * Uses (ITK Request):
		 * trackingId
		 * 
		 * Uses (ITK Inf ack):
		 * trackingIdRef
		 * 
		 * Uses (ITK Bus Ack):
		 * hl7:conveyingTransmission -> id
		 * 
		 * This ID is used throughout the ITK message interactions and spans
		 * multiple ebXml interactions (the ebXml conversationId 'may' change).
		 * <p>
		 * Presumably the same trackingId should be used when resending messages
		 * at the ITK protocol level (e.g. if an ebXml ack was received but
		 * no ITK acks were received). In this case a new ebXml messageId would
		 * be used - possibly a new ebXml conversationId too.
		 * <p>
		 * For the time being however, resends at the ITK protocol level are out of scope.
		 */
		private final String itkCorrelationId = generateId();
		
		/**
		 * Uses (ITK request)
		 * manifestitem id
		 * payload id
		 * Also - internal ID from ClinicalDocument but without the uuid_ prefix
		 * 
		 * Uses (ITK bus ack):
		 * conveyingTransmition -> id
		 * (This refers to the payload id (with uuid_ prefix) - not the internal ClinicalDocument id)
		 * 
		 * TODO: Are these IDs related as a convenience or is it required by the protocol
		 */
		private final String itkDocumentId = generateId();
		
		/**
		 * Document payload
		 * <p>
		 * If document type is NOT xml additional encoding may be required to 
		 * escape reserved XML characters
		 * 
		 * TODO: Check documentation for further details on GZIP encoding
		 */
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
