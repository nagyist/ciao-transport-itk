package uk.nhs.ciao.transport.spine.hl7;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.ObjectCreateRule;
import org.apache.commons.digester3.Rule;
import org.apache.commons.digester3.RulesBase;
import org.apache.commons.digester3.SetPropertiesRule;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Parses an ITK distribution envelope document into a corresponding {@link HL7Part}.
 * <p>
 * Instances of this class are <strong>not</strong> thread-safe.
 */
public class HL7PartParser {
	private static final String HL7_URI = "urn:hl7-org:v3";
	
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
	public HL7PartParser() throws ParserConfigurationException, SAXException {
		this(SAXParserFactory.newInstance());
	}
	
	/**
	 * Creates a new parser using the specified SAXParserFactory
	 */
	public HL7PartParser(final SAXParserFactory parserFactory)
			throws ParserConfigurationException, SAXException {
		parserFactory.setNamespaceAware(true);
		parserFactory.setValidating(false);
		
		rules = new RulesBase() {
			@Override
			public List<Rule> match(final String namespaceURI, final String pattern,
					final String name, final Attributes attributes) {
				// Strip the first part of the pattern away and replace with a known static value
				// (this is dynamic and causes problems with matching rules)				
				final int index = pattern.indexOf("/");
				final String normalisedPattern;
				if (index < 0) {
					normalisedPattern = "hl7Part";
				} else {
					normalisedPattern = "hl7Part" + pattern.substring(index);
				}
				
				// Match on the normalised pattern
				return super.match(namespaceURI, normalisedPattern, name, attributes);
			}
		};
		parser = parserFactory.newSAXParser();

		registerRules();
	}
	
	/**
	 * Parses the specified XML input stream into a corresponding {@link HL7Part} instance.
	 * 
	 * @param in The input stream to parse
	 * @return An {@link HL7Part} corresponding to <code>in</code>
	 * @throws IOException If the stream could not be read, or if the stream represents an invalid XML document
	 */
	public HL7Part parse(final InputStream in) throws IOException {
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
			
			return (HL7Part)digester.parse(in);
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
	private void registerRules() throws ParserConfigurationException {		
		rules.setNamespaceURI(HL7_URI);

		rules.add("hl7Part", new ObjectCreateRule(HL7Part.class));
		rules.add("hl7Part/id", new SetPropertiesRule("root", "id"));
		rules.add("hl7Part/creationTime", new SetPropertiesRule("value", "creationTime"));
		rules.add("hl7Part/interactionId", new SetPropertiesRule("extension", "interactionId"));
		
		rules.add("hl7Part/communicationFunctionRcv/device/id", new SetPropertiesRule("extension", "receiverAsid"));
		rules.add("hl7Part/communicationFunctionSnd/device/id", new SetPropertiesRule("extension", "senderAsid"));
		rules.add("hl7Part/ControlActEvent/author1/AgentSystemSDS/agentSystemSDS/id", new SetPropertiesRule("extension", "agentSystemAsid"));
	}
	
	/**
	 * Resets state so that the underlying parser can be reused
	 */
	private void reset() {
		this.parser.reset();
	}
}
