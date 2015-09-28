package uk.nhs.ciao.transport.spine.itk;
import static org.apache.camel.builder.PredicateBuilder.*;

import java.io.File;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.util.toolbox.AggregationStrategies;

import uk.nhs.ciao.camel.CamelUtils;

/**
 * Example route simulating multiple file producers attempting to write a file with the same name
 * concurrently. This could occur when writing inf/bus acks to disc (especially if the spec is
 * updated to allow multiple responses)
 * <p>
 * A possible solution is to register an error handler configured to retry N times. However,
 * because camel will retry from the point of failure rather than the start of the route 
 * (http://camel.apache.org/exception-clause.html) two separate routes may need to be used.
 * The second (main processing) route needs to have it's default error handler removed so that
 * the initial (error handling) route triggers a full retry of the processing route.
 */
public class CompetingFileProducerExample {
	public static void main(final String[] args) throws Exception {
		final CamelContext context = new DefaultCamelContext();		
		final ProducerTemplate producerTemplate = new DefaultProducerTemplate(context);
		try {
			context.addRoutes(new Builder());
			context.start();
			producerTemplate.start();
			
			producerTemplate.sendBody("direct:store", "real-content");
		} finally {
			CamelUtils.stopQuietly(context, producerTemplate);
		}
	}
	
	public static class Builder extends RouteBuilder {
		@Override
		public void configure() throws Exception {
			from("direct:store")
				.errorHandler(defaultErrorHandler()
						.maximumRedeliveries(6)
						.redeliveryDelay(200))
				.to("direct:store-impl");
			
			from("direct:store-impl")
				.errorHandler(noErrorHandler())
				
				// calculate the file name to write to - generated based on the number or files in the folder
				.setHeader(Exchange.FILE_PATH, constant("folder"))
				.bean(CompetingFileProducerExample.class, "calculateFileName")
				.removeHeader(Exchange.FILE_PATH)
				
				.log("Redelivery count: ${header.CamelRedeliveryCounter}")
				
				// Simulate another process getting in the way by writing to the file
				// After 4 tries just continue - the main process will then get a clean file write
				// Changing this to 7 or higher results in the main processes running out of retry attempts
				// and re-throwing the exception
				.choice()
					.when(or(not(header(Exchange.REDELIVERY_COUNTER)), simple("${header.CamelRedeliveryCounter} < 7")))
						.log("Simulating a write from another process!")
						.multicast(AggregationStrategies.useOriginal())
							.pipeline()
								.setBody(constant("injected text"))
								.to("file://?fileExist=Override")
							.end()
						.end()
					.endChoice()
				.end()				
				
				.log("Attempting to write to file: ${header.CamelFileName}")
				.to("file://?fileExist=Fail");
		}
	}
	
	public static void calculateFileName(final Message message, @Header(Exchange.FILE_PATH) final File folder) {
		int count;
		
		if (folder == null || !folder.isDirectory()) {
			count = 0;
		} else {
			count = folder.list().length;
		}
		
		final File file = new File(folder, "name-" + (++count));		
		message.setHeader(Exchange.FILE_NAME, file.toURI().getRawPath());
	}
	
}
