package uk.nhs.ciao.transport.spine.trunk;

import com.google.common.base.Preconditions;

import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.docs.parser.ParsedDocument;
import uk.nhs.ciao.exceptions.CIAOConfigurationException;

/**
 * Constructs {@link TrunkRequestProperties} instances by combining
 * a CIAO configuration (for defaults) and an incoming {@link ParsedDocument}
 */
public class TrunkRequestPropertiesFactory {
	private final CIAOConfig config;
	
	public TrunkRequestPropertiesFactory(final CIAOConfig config) {
		this.config = Preconditions.checkNotNull(config);
	}
	
	public TrunkRequestProperties newTrunkRequestProperties(final ParsedDocument parsedDocument) throws CIAOConfigurationException {
		return new TrunkRequestProperties(config, parsedDocument);
	}
}
