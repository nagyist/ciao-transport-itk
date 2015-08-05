package uk.nhs.ciao.transport.spine.route;

import org.apache.camel.builder.RouteBuilder;

import com.google.common.base.Strings;

/**
 * Abstract class which provides base / helper methods for route builder implementations
 */
public abstract class BaseRouteBuilder extends RouteBuilder {
	private String internalRoutePrefix;
	
	/**
	 * Path prefix added to all generated internal route URLs (to ensure uniqueness)
	 */
	public String getInternalRoutePrefix() {
		return internalRoutePrefix;
	}
	
	/**
	 * Path prefix added to all generated internal route URLs (to ensure uniqueness)
	 */
	public void setInternalRoutePrefix(final String internalRoutePrefix) {
		this.internalRoutePrefix = internalRoutePrefix;
	}
	
	protected String internalUri(final String scheme, final String path) {
		final StringBuilder builder = new StringBuilder(scheme).append(":");
		if (!Strings.isNullOrEmpty(internalRoutePrefix)) {
			builder.append(internalRoutePrefix).append("/");
		}
		builder.append(path);
		return builder.toString();
	}
	
	protected String internalDirectUri(final String path) {
		return internalUri("direct", path);
	}
	
	protected String internalSedaUri(final String path) {
		return internalUri("seda", path);
	}
}
