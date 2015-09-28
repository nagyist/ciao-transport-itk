package uk.nhs.ciao.transport.itk.envelope;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.NodeCreateRule;
import org.apache.commons.digester3.ObjectCreateRule;
import org.apache.commons.digester3.Rule;
import org.apache.commons.digester3.RulesBase;
import org.apache.commons.digester3.SetNextRule;
import org.apache.commons.digester3.SetPropertiesRule;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import uk.nhs.ciao.transport.itk.envelope.DistributionEnvelope.HandlingSpec;
import uk.nhs.ciao.transport.itk.envelope.DistributionEnvelope.ManifestItem;
import uk.nhs.ciao.transport.itk.envelope.DistributionEnvelope.Payload;

/**
 * Parses an ITK distribution envelope document into a corresponding {@link DistributionEnvelope}.
 * <p>
 * Instances of this class are <strong>not</strong> thread-safe.
 */
public class DistributionEnvelopeParser {
	private static final String ITK_URI = "urn:nhs-itk:ns:201005";
	
	/**
	 * XML parser
	 * <p>
	 * The parser instance is reset and re-used when parsing a new document
	 */
	private final SAXParser parser;
	
	/**
	 * XML transformer (for re-serializing the payload)
	 * <p>
	 * The transformer instance is reset and re-used when parsing a new document
	 */
	private final Transformer transformer;
	
	/**
	 * Digester rules to match when parsing incoming XML
	 */
	private final RulesBase rules;

	/**
	 * Creates a new parser using the default SAXParserFactory and TransformerFactory
	 */
	public DistributionEnvelopeParser() throws ParserConfigurationException, SAXException, TransformerException {
		this(SAXParserFactory.newInstance(), TransformerFactory.newInstance());
	}
	
	/**
	 * Creates a new parser using the specified SAXParserFactory
	 * @throws TransformerException 
	 */
	public DistributionEnvelopeParser(final SAXParserFactory parserFactory, final TransformerFactory transformerFactory)
			throws ParserConfigurationException, SAXException, TransformerException {
		parserFactory.setNamespaceAware(true);
		parserFactory.setValidating(false);
		
		this.transformer = transformerFactory.newTransformer();
		
		rules = new RulesBase();
		parser = parserFactory.newSAXParser();

		registerRules();
	}
	
	/**
	 * Parses the specified XML input stream into a corresponding {@link DistributionEnvelope} instance.
	 * 
	 * @param in The input stream to parse
	 * @return An {@link DistributionEnvelope} corresponding to <code>in</code>
	 * @throws IOException If the stream could not be read, or if the stream represents an invalid XML document
	 */
	public DistributionEnvelope parse(final InputStream in) throws IOException {
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
			
			return (DistributionEnvelope)digester.parse(in);
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
	private void registerRules() throws ParserConfigurationException, TransformerConfigurationException {
		rules.setNamespaceURI(ITK_URI);
		rules.add("DistributionEnvelope", new ObjectCreateRule(DistributionEnvelope.class));

		rules.add("DistributionEnvelope/header", new SetPropertiesRule("service", "service"));
		rules.add("DistributionEnvelope/header", new SetPropertiesRule("trackingid", "trackingId"));
		
		rules.add("DistributionEnvelope/header/addresslist/address", new ObjectCreateRule(Address.class));
		rules.add("DistributionEnvelope/header/addresslist/address", new SetPropertiesRule("uri", "uri"));
		rules.add("DistributionEnvelope/header/addresslist/address", new SetPropertiesRule("type", "type"));
		rules.add("DistributionEnvelope/header/addresslist/address", new SetNextRule("addAddress"));
		
		rules.add("DistributionEnvelope/header/auditIdentity", new ObjectCreateRule(Identity.class));
		rules.add("DistributionEnvelope/header/auditIdentity/id", new SetPropertiesRule("uri", "uri"));
		rules.add("DistributionEnvelope/header/auditIdentity/id", new SetPropertiesRule("type", "type"));
		rules.add("DistributionEnvelope/header/auditIdentity", new SetNextRule("setAuditIdentity"));
		
		rules.add("DistributionEnvelope/header/manifest/manifestitem", new ObjectCreateRule(ManifestItem.class));
		rules.add("DistributionEnvelope/header/manifest/manifestitem", new SetPropertiesRule("mimetype", "mimeType"));
		rules.add("DistributionEnvelope/header/manifest/manifestitem", new SetPropertiesRule("id", "id"));
		rules.add("DistributionEnvelope/header/manifest/manifestitem", new SetPropertiesRule("profileid", "profileId"));
		rules.add("DistributionEnvelope/header/manifest/manifestitem", new SetPropertiesRule("base64", "base64"));
		rules.add("DistributionEnvelope/header/manifest/manifestitem", new SetPropertiesRule("compressed", "compressed"));
		rules.add("DistributionEnvelope/header/manifest/manifestitem", new SetPropertiesRule("encrypted", "encrypted"));
		rules.add("DistributionEnvelope/header/manifest/manifestitem", new SetNextRule("addManifestItem"));
		
		rules.add("DistributionEnvelope/header/senderAddress", new ObjectCreateRule(Address.class));
		rules.add("DistributionEnvelope/header/senderAddress", new SetPropertiesRule("uri", "uri"));
		rules.add("DistributionEnvelope/header/senderAddress", new SetPropertiesRule("type", "type"));
		rules.add("DistributionEnvelope/header/senderAddress", new SetNextRule("setSenderAddress"));
		
		rules.add("DistributionEnvelope/header/handlingSpecification/spec", new HandlingSpecRule());
		
		rules.add("DistributionEnvelope/payloads/payload", new PayloadRule());
	}
	
	/**
	 * Resets state so that the underlying parser and transformer can be reused
	 */
	private void reset() {
		this.parser.reset();
		this.transformer.reset();
	}
	
	// Rule helper methods
	
	private String getValue(final Attributes attributes, final String name) {
		String value = attributes.getValue(ITK_URI, name);
		if (value == null) {
			value = attributes.getValue(name);
		}
		return value;
	}
	
	private String serialize(final Node node) throws TransformerException {
		final String value;
		
		if (node instanceof Element) {
			final Element element = (Element) node;
			final StringWriter writer = new StringWriter();

			transformer.reset();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.transform(new DOMSource(element), new StreamResult(writer));
			value = writer.toString();
		} else {
			value = node.getTextContent();
		}
		
		return value;
	}

	// Custom rule classes
	
	/**
	 * Sets handling spec properties (from XML attributes) when an handlingSpecification/spec element is seen
	 */
	private class HandlingSpecRule extends Rule {
		@Override
		public void begin(final String namespace, final String name,
				final Attributes attributes) throws Exception {
			final DistributionEnvelope envelope = getDigester().<DistributionEnvelope>peek();
			final HandlingSpec handlingSpec = envelope.getHandlingSpec();

			final String key = getValue(attributes, "key");
			final String value = getValue(attributes, "value");
			if (key != null) {
				handlingSpec.set(key, value);
			}
		}
	}
	
	/**
	 * Sets payload and nested payload body when an payload element is seen
	 * <p>
	 * Requires special handling since the payload content may itself be
	 * an in-line XML document
	 */
	public class PayloadRule extends NodeCreateRule {
		public PayloadRule() throws ParserConfigurationException {
			super(Node.ELEMENT_NODE);
		}
		
		@Override
		public void begin(String namespaceURI, String name, Attributes attributes) throws Exception {
			final Payload payload = new Payload();
			final String id = getValue(attributes, "id");
			if (id != null) {
				payload.setId(id);
			}
			
			getDigester().push(payload);
			
			// delegate to super class to gather body as an element
			super.begin(namespaceURI, name, attributes);
		}

		@Override
		public void end(final String namespace, final String name) throws Exception {
			// gather body from built element
			final String body = getBody();
			
			// allow supper class to clean up the stack
			super.end(namespace, name);

			// process the body
			final Payload payload = getDigester().<Payload> pop();
			payload.setBody(body);
			
			getDigester().<DistributionEnvelope>peek().addPayload(payload);
		}

		/**
		 * (Re)serializes the body content of this node
		 * <p>
		 * This could be a single text node, or maybe a whole
		 * in-line XML document
		 */
		private String getBody() throws Exception {
			// The built element is the <payload>
			// The payload body is the concatenation of all the child nodes
			final Element containerElement = getDigester().<Element> peek();
			final StringBuilder builder = new StringBuilder();

			final NodeList children = containerElement.getChildNodes();
			for (int index = 0; index < children.getLength(); index++) {
				final Node child = children.item(index);
				builder.append(serialize(child));
			}

			return builder.toString();
		}
	}
}
