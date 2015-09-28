package uk.nhs.ciao.transport.itk.envelope;

import java.io.IOException;
import java.io.StringWriter;

import com.google.common.io.Closeables;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * Serializes an {@link InfrastructureResponse} into an XML string
 */
public class InfrastructureResponseSerializer {
	private final Template template;
	
	/**
	 * Constructs a new serializer using a default freemarker configuration
	 * 
	 * @throws IOException If the infrastructureResponse template could not be loaded
	 */
	public InfrastructureResponseSerializer() throws IOException {
		this(createDefaultConfiguration());
	}
	
	/**
	 * Constructs a new serializer using the specified freemarker configuration
	 * 
	 * @throws IOException If the ebxmlEnvelope template could not be loaded
	 */
	public InfrastructureResponseSerializer(final Configuration configuration) throws IOException {
		this.template = configuration.getTemplate("infrastructureResponse.ftl");
	}
	
	/**
	 * Serializes an InfrastructureResponse into an xml string
	 */
	public String serialize(final InfrastructureResponse envelope) throws IOException {
		final StringWriter writer = new StringWriter();
		try {
			template.process(envelope, writer);
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
		configuration.setClassForTemplateLoading(InfrastructureResponseSerializer.class, "");
		return configuration;
	}
}
