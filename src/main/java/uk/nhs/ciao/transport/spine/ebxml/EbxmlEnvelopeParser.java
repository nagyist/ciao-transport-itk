package uk.nhs.ciao.transport.spine.ebxml;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.digester3.BeanPropertySetterRule;
import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.ObjectCreateRule;
import org.apache.commons.digester3.Rule;
import org.apache.commons.digester3.RulesBase;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope.ErrorDetail;
import uk.nhs.ciao.transport.spine.ebxml.EbxmlEnvelope.ManifestReference;

/**
 * Parses a SOAP/ebXml document into a corresponding {@link EbxmlEnvelope}.
 * <p>
 * Instances of this class are <strong>not</strong> thread-safe.
 */
public class EbxmlEnvelopeParser {
	private static final String SOAP_URI = "http://schemas.xmlsoap.org/soap/envelope/";
	private static final String EBXML_URI = "http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd";
	private static final String XLINK_URI = "http://www.w3.org/1999/xlink";
	private static final String HL7EBML_URI = "urn:hl7-org:transport/ebxml/DSTUv1.0";
	
	/**
	 * XML parser
	 * <p>
	 * The parser instance is reset and re-used when parsing a new document
	 */
	private final SAXParser parser;
	
	/**
	 * Digester rules to match when parsing incoming XML
	 */
	private final RulesBase rules;

	/**
	 * Creates a new parser using the default SAXParserFactory
	 */
	public EbxmlEnvelopeParser() throws ParserConfigurationException, SAXException {
		this(SAXParserFactory.newInstance());
	}
	
	/**
	 * Creates a new parser using the specified SAXParserFactory
	 */
	public EbxmlEnvelopeParser(final SAXParserFactory factory) throws ParserConfigurationException, SAXException {
		factory.setNamespaceAware(true);
		factory.setValidating(false);
		
		rules = new RulesBase();
		parser = factory.newSAXParser();

		registerRules();
	}
	
	/**
	 * Parses the specified XML input stream into a corresponding {@link EbxmlEnvelope} instance.
	 * 
	 * @param in The input stream to parse
	 * @return An {@link EbxmlEnvelope} corresponding to <code>in</code>
	 * @throws IOException If the stream could not be read, or if the stream represents an invalid XML document
	 */
	public EbxmlEnvelope parse(final InputStream in) throws IOException {
		try {
			reset();
			
			/*
			 * According to the Digester wiki (http://wiki.apache.org/commons/Digester/FAQ) digester instance
			 * are not safe to reuse - however it is possible to reuse the SAX parser (if reset) and a 
			 * previously created set of rules. Although reusable - these instances are NOT thread-safe!
			 */
			final Digester digester = new Digester(parser);
			digester.setNamespaceAware(true);
			digester.setValidating(false);
			digester.setRules(rules);
			
			return (EbxmlEnvelope)digester.parse(in);
		} catch (SAXException e) {
			throw new IOException(e);
		} finally {
			reset();
		}
	}
	
	/**
	 * Registers a set of rules to fire as documents are parsed
	 * <p>
	 * The registered rule instances will be reused across multiple document parses
	 */
	private void registerRules() {
		rules.setNamespaceURI(SOAP_URI);
		rules.add("Envelope", new ObjectCreateRule(EbxmlEnvelope.class));
		
		rules.setNamespaceURI(EBXML_URI);
		rules.add("Envelope/Header/MessageHeader/From/PartyId", new BeanPropertySetterRule("fromParty"));
		rules.add("Envelope/Header/MessageHeader/To/PartyId", new BeanPropertySetterRule("toParty"));
		rules.add("Envelope/Header/MessageHeader/CPAId", new BeanPropertySetterRule("cpaId"));
		rules.add("Envelope/Header/MessageHeader/ConversationId", new BeanPropertySetterRule("conversationId"));
		rules.add("Envelope/Header/MessageHeader/Service", new BeanPropertySetterRule("service"));
		rules.add("Envelope/Header/MessageHeader/Action", new BeanPropertySetterRule("action"));
		rules.add("Envelope/Header/MessageHeader/DuplicateElimination", new DuplicateEliminationRule());
		
		rules.add("Envelope/Header/MessageHeader/MessageData", new MessageDataRule());		
		rules.add("Envelope/Header/MessageHeader/MessageData/MessageId", new BeanPropertySetterRule("messageId"));
		rules.add("Envelope/Header/MessageHeader/MessageData/Timestamp", new BeanPropertySetterRule("timestamp"));
		rules.add("Envelope/Header/MessageHeader/MessageData/RefToMessageId", new BeanPropertySetterRule("refToMessageId"));
		
		rules.add("Envelope/Header/AckRequested", new AckRequestedRule());
		rules.add("Envelope/Header/Acknowledgment", new AcknowledgmentRule());
		
		rules.add("Envelope/Header/ErrorList", new ErrorListRule());		
		rules.add("Envelope/Header/ErrorList/Error", new ErrorRule());
		rules.add("Envelope/Header/ErrorList/Error/Description", new BeanPropertySetterRule("description"));
		
		rules.add("Envelope/Body/Manifest/Reference", new ManifestReferenceRule());
		rules.add("Envelope/Body/Manifest/Reference/Description", new BeanPropertySetterRule("description"));
		
		rules.setNamespaceURI(HL7EBML_URI);
		rules.add("Envelope/Body/Manifest/Reference/Payload", new HL7ManifestRule());
	}
	
	/**
	 * Resets state so that the underlying parser can be reused
	 */
	private void reset() {
		this.parser.reset();
	}
	
	// Custom rule classes
	
	/**
	 * Pushes the MessageData instance onto the stack when a MessageData element is seen
	 */
	private class MessageDataRule extends Rule {
		@Override
		public void begin(final String namespace, final String name,
				final Attributes attributes) throws Exception {
			final EbxmlEnvelope envelope = getDigester().peek();
			getDigester().push(envelope.getMessageData());
		}
		
		@Override
		public void end(final String namespace, final String name) throws Exception {
			getDigester().pop();
		}
	}
	
	/**
	 * Marks the envelope as requiring duplicate elimination logic
	 */
	private class DuplicateEliminationRule extends Rule {
		@Override
		public void begin(final String namespace, final String name,
				final Attributes attributes) throws Exception {
			getDigester().<EbxmlEnvelope>peek().setDuplicateElimination(true);
		}
	}
	
	/**
	 * Marks the envelope as wanting an acknowledgement when an AckRequested element is seen
	 */
	private class AckRequestedRule extends Rule {
		@Override
		public void begin(final String namespace, final String name,
				final Attributes attributes) throws Exception {
			getDigester().<EbxmlEnvelope>peek().setAckRequested(true);
		}
	}
	
	/**
	 * Marks the envelope as an acknowledgement when an Acknowledgement element is seen
	 */
	private class AcknowledgmentRule extends Rule {
		@Override
		public void begin(final String namespace, final String name,
				final Attributes attributes) throws Exception {
			getDigester().<EbxmlEnvelope>peek().setAcknowledgment(true);
		}
	}
	
	/**
	 * Creates and pushes an Error instance onto the stack when an ErrorList element is seen
	 */
	private class ErrorListRule extends Rule {
		@Override
		public void begin(String namespace, String name,
				Attributes attributes) throws Exception {
			final ErrorDetail error = getDigester().<EbxmlEnvelope>peek().addError();
			getDigester().push(error);
			
			final String listId = attributes.getValue(EBXML_URI, "id");
			if (listId != null) {
				error.setListId(listId);
			}
		}
		
		@Override
		public void end(String namespace, String name) throws Exception {
			getDigester().pop();
		}
	}
	
	/**
	 * Sets Error properties (from XML attributes) when an Error element is seen
	 */
	private class ErrorRule extends Rule {
		@Override
		public void begin(final String namespace, final String name,
				final Attributes attributes) throws Exception {
			final ErrorDetail error = getDigester().<ErrorDetail>peek();

			final String id = attributes.getValue(EBXML_URI, "id");
			if (id != null) {
				error.setId(id);
			}
			
			final String code = attributes.getValue(EBXML_URI, "errorCode");
			if (code != null) {
				error.setCode(code);
			}
			
			final String severity = attributes.getValue(EBXML_URI, "severity");
			if (severity != null) {
				error.setSeverity(severity);
			}
			
			final String codeContext = attributes.getValue(EBXML_URI, "codeContext");
			if (codeContext != null) {
				error.setCodeContext(codeContext);
			}
		}
	}
	
	/**
	 * Creates and pushes an ManifestReference instance onto the stack when an Reference element is seen
	 */
	private class ManifestReferenceRule extends Rule {
		@Override
		public void begin(String namespace, String name,
				Attributes attributes) throws Exception {
			final ManifestReference reference = getDigester().<EbxmlEnvelope>peek().addManifestReference();
			getDigester().push(reference);
			
			final String href = attributes.getValue(XLINK_URI, "href");
			if (href != null) {
				reference.setHref(href);
			}
		}
		
		@Override
		public void end(String namespace, String name) throws Exception {
			getDigester().pop();
		}
	}
	
	/**
	 * Marks the current ManifestEntry as HL7 when as EBXMLHL7 Payload element is seen
	 */
	private class HL7ManifestRule extends Rule {
		@Override
		public void begin(String namespace, String name,
				Attributes attributes) throws Exception {
			getDigester().<ManifestReference>peek().setHl7(true);
		}		
	}
}
