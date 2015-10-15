package uk.nhs.ciao.transport.dts.route;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

import uk.nhs.ciao.camel.CamelUtils;
import uk.nhs.ciao.camel.URIBuilder;
import uk.nhs.ciao.transport.dts.processor.DTSDataFilePoller;

/**
 * Example class checking how to handle incoming pairs of DTS *.ctl and *.dat files
 * <p>
 * Will eventually be moved into main routes classes
 */
public class DTSIncomingFileExample extends RouteBuilder {
	public static void main(final String[] args) throws Exception {
		final CamelContext context = new DefaultCamelContext();
		try {
			context.addRoutes(new DTSIncomingFileExample());
			context.start();
			new CountDownLatch(1).await();
		} finally {
			CamelUtils.stopQuietly(context);
		}
	}
	
	@Override
	public void configure() throws Exception {
		final ScheduledExecutorService executorService = getContext().getExecutorServiceManager()
				.newSingleThreadScheduledExecutor(this, "data-file-poller");
		
		final URIBuilder uri = new URIBuilder("file://./target/example")
			.set("idempotent", "true")
			.set("readLock", "idempotent")
			.set("move", "done")
			.set("moveFailed", "error")
			.set("include", "..*\\.ctl"); // .+ does NOT work!
		
		from(uri.toString())
			.log("Found control file: ${header.CamelFileName}")
			
			.setHeader("controlFileName").header(Exchange.FILE_NAME)
			.setHeader(DTSDataFilePoller.HEADER_FOLDER_NAME).header(Exchange.FILE_PARENT)
			.setHeader(DTSDataFilePoller.HEADER_FILE_NAME, ExpressionBuilder.regexReplaceAll(simple("${header.CamelFileName}"),
					"(..*)\\.ctl", "$1.dat"))
			
			.process(new DTSDataFilePoller(executorService, 200, 5 * 20))
		.end();
	}
}
