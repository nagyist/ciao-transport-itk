package uk.nhs.ciao.transport.spine.itk;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.digester3.BeanPropertySetterRule;
import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.ObjectCreateRule;
import org.apache.commons.digester3.RulesBase;
import org.apache.commons.digester3.SetNextRule;
import org.apache.commons.digester3.SetPropertiesRule;
import org.xml.sax.SAXException;

import uk.nhs.ciao.transport.spine.itk.InfrastructureResponse.ErrorInfo;

/**
 * Parses an ITK infrastructure response document into a corresponding {@link InfrastructureResponse}.
 * <p>
 * Instances of this class are <strong>not</strong> thread-safe.
 */
public class InfrastructureResponseParser {
	private static final String ITK_URI = "urn:nhs-itk:ns:201005";
	
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
	public InfrastructureResponseParser() throws ParserConfigurationException, SAXException {
		this(SAXParserFactory.newInstance());
	}
	
	/**
	 * Creates a new parser using the specified SAXParserFactory
	 */
	public InfrastructureResponseParser(final SAXParserFactory parserFactory)
			throws ParserConfigurationException, SAXException {
		parserFactory.setNamespaceAware(true);
		parserFactory.setValidating(false);
		
		rules = new RulesBase();
		parser = parserFactory.newSAXParser();

		registerRules();
	}
	
	/**
	 * Parses the specified XML input stream into a corresponding {@link InfrastructureResponse} instance.
	 * 
	 * @param in The input stream to parse
	 * @return An {@link InfrastructureResponse} corresponding to <code>in</code>
	 * @throws IOException If the stream could not be read, or if the stream represents an invalid XML document
	 */
	public InfrastructureResponse parse(final InputStream in) throws IOException {
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
			
			return (InfrastructureResponse)digester.parse(in);
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
		rules.setNamespaceURI(ITK_URI);
		rules.add("InfrastructureResponse", new ObjectCreateRule(InfrastructureResponse.class));

		rules.add("InfrastructureResponse", new SetPropertiesRule("result", "result"));
		rules.add("InfrastructureResponse", new SetPropertiesRule("timestamp", "timestamp"));
		rules.add("InfrastructureResponse", new SetPropertiesRule("serviceRef", "serviceRef"));
		rules.add("InfrastructureResponse", new SetPropertiesRule("trackingIdRef", "trackingIdRef"));
		
		rules.add("InfrastructureResponse/reportingIdentity", new ObjectCreateRule(Identity.class));
		rules.add("InfrastructureResponse/reportingIdentity/id", new SetPropertiesRule("uri", "uri"));
		rules.add("InfrastructureResponse/reportingIdentity/id", new SetPropertiesRule("type", "type"));
		rules.add("InfrastructureResponse/reportingIdentity", new SetNextRule("setReportingIdentity"));
		
		rules.add("InfrastructureResponse/errors/errorInfo", new ObjectCreateRule(ErrorInfo.class));
		rules.add("InfrastructureResponse/errors/errorInfo/ErrorID", new BeanPropertySetterRule("id"));
		rules.add("InfrastructureResponse/errors/errorInfo/ErrorCode", new BeanPropertySetterRule("code"));
		rules.add("InfrastructureResponse/errors/errorInfo/ErrorCode", new SetPropertiesRule("codeSystem", "codeSystem"));
		rules.add("InfrastructureResponse/errors/errorInfo/ErrorText", new BeanPropertySetterRule("text"));
		rules.add("InfrastructureResponse/errors/errorInfo/ErrorDiagnosticText", new BeanPropertySetterRule("diagnosticText"));
		rules.add("InfrastructureResponse/errors/errorInfo", new SetNextRule("addError"));
	}
	
	/**
	 * Resets state so that the underlying parser and transformer can be reused
	 */
	private void reset() {
		this.parser.reset();
	}
}
