package uk.nhs.ciao.transport.spine.trunk;

import java.util.Map;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;

import uk.nhs.ciao.configuration.CIAOConfig;
import uk.nhs.ciao.exceptions.CIAOConfigurationException;
import uk.nhs.ciao.util.TreeMerge;

/**
 * Enriches incoming JSON with properties specified via CIAOConfig
 */
public class ConfigPropertiesEnricher {
	private final Map<String, String> defaultProperties;
	private final TreeMerge treeMerge;
	
	public ConfigPropertiesEnricher(final CIAOConfig config) throws CIAOConfigurationException {
		defaultProperties = Maps.newLinkedHashMap();
		treeMerge = new TreeMerge();
		
		addProperty(config, "senderPartyId");
		addProperty(config, "senderAsid");
		addProperty(config, "senderODSCode");
		addProperty(config, "auditODSCode");
		addProperty(config, "interactionId");
	}
	
	private void addProperty(final CIAOConfig config, final String configKey) throws CIAOConfigurationException {
		final String jsonPropertyName = configKey;
		addProperty(config, configKey, jsonPropertyName);
	}
	
	private void addProperty(final CIAOConfig config, final String configKey, final String jsonPropertyName) throws CIAOConfigurationException {
		if (config.getConfigKeys().contains(configKey)) {
			final String value = config.getConfigValue(configKey);
			if (!Strings.isNullOrEmpty(value)) {
				defaultProperties.put(jsonPropertyName, value);
			}
		}
	}
	
	public Map<String, Object> enrich(final Map<String, Object> json) {
		final Object properties = json.get("properties");
		if (properties instanceof Map<?, ?>) {
			@SuppressWarnings("unchecked")
			final Map<String, Object> destination = (Map<String, Object>) properties;
			treeMerge.mergeInto(defaultProperties, destination);
		}
		
		return json;
	}
}
