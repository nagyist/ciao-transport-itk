package uk.nhs.ciao.transport.spine.hl7;

import java.io.IOException;
import java.io.StringWriter;

import com.google.common.io.Closeables;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * Serializes an {@link HL7Part} into an XML string
 */
public class HL7PartSerializer {
	private final Template template;
	
	/**
	 * Constructs a new serializer using a default freemarker configuration
	 * 
	 * @throws IOException If the hl7Part template could not be loaded
	 */
	public HL7PartSerializer() throws IOException {
		this(createDefaultConfiguration());
	}
	
	/**
	 * Constructs a new serializer using the specified freemarker configuration
	 * 
	 * @throws IOException If the hl7Part template could not be loaded
	 */
	public HL7PartSerializer(final Configuration configuration) throws IOException {
		this.template = configuration.getTemplate("hl7Part.ftl");
	}
	
	/**
	 * Serializes an HL7Part into an xml string
	 */
	public String serialize(final HL7Part part) throws IOException {
		final StringWriter writer = new StringWriter();
		try {
			template.process(part, writer);
			writer.flush();
			return writer.toString();
		} catch (TemplateException e) {
			throw new IOException(e);
		} finally {
			final boolean swallowIOException = true;
			Closeables.close(writer, swallowIOException);
		}
	}
	
	/**
	 * Creates a default freemarker configuration which uses this class for
	 * loading templates
	 */
	private static Configuration createDefaultConfiguration() {
		final Configuration configuration = new Configuration();
		configuration.setClassForTemplateLoading(HL7PartSerializer.class, "");
		return configuration;
	}
}
