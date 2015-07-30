package uk.nhs.ciao.transport.spine.itk;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.ObjectCreateRule;
import org.apache.commons.digester3.RulesBase;
import org.xml.sax.SAXException;

/**
 * Parses an ITK distribution envelope document into a corresponding {@link DistributionEnvelope}.
 * <p>
 * Instances of this class are <strong>not</strong> thread-safe.
 */
public class DistributionEnvelopeParser {
	private static final String ITK_URI = "urn:nhs-itk:ns:201005";
	
	/**
	 * XML digester
	 * <p>
	 * The digester instance is reset and re-used when parsing a new document
	 */
	private final SAXParser parser;
	
	/**
	 * Digester rules to match when parsing incoming XML
	 */
	private final RulesBase rules;

	/**
	 * Creates a new parser using the default SAXParserFactory
	 */
	public DistributionEnvelopeParser() throws ParserConfigurationException, SAXException {
		this(SAXParserFactory.newInstance());
	}
	
	/**
	 * Creates a new parser using the specified SAXParserFactory
	 */
	public DistributionEnvelopeParser(final SAXParserFactory factory) throws ParserConfigurationException, SAXException {
		factory.setNamespaceAware(true);
		factory.setValidating(false);
		
		rules = new RulesBase();
		parser = factory.newSAXParser();

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
	private void registerRules() {
		rules.setNamespaceURI(ITK_URI);
		rules.add("DistributionEnvelope", new ObjectCreateRule(DistributionEnvelope.class));

		// TODO: parse the document
	}
	
	/**
	 * Resets state so that the underlying parser can be reused
	 */
	private void reset() {
		this.parser.reset();
	}
	
	// Custom rule classes

}
