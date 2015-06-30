package uk.nhs.ciao.transport.spine.example;

import java.util.Collections;
import java.util.Set;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpOperationFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultConsumerTemplate;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.camel.spring.spi.TransactionErrorHandlerBuilder;
import org.slf4j.LoggerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.connection.JmsTransactionManager;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.SettableFuture;

/**
 * Example application to check routes required to support
 * retries, fail-over, and transaction management for
 * the sender role in an 'End-Party Reliability' pattern.
 */
public class EndPartyReliabilitySenderApplication {	
	private final CamelContext context;
	private final ConsumerTemplate consumerTemplate;
	
	public EndPartyReliabilitySenderApplication() throws Exception {
		final SimpleRegistry registry = new SimpleRegistry();
		
		this.context = new DefaultCamelContext(registry);
		context.setTracing(true);
		this.consumerTemplate = new DefaultConsumerTemplate(context);
		
		final ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory();
		connectionFactory.setBrokerURL("tcp://localhost:61616");
		registry.put("jmsConnectionFactory", connectionFactory);
		
		final CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory(connectionFactory);		
		final JmsTransactionManager transactionManager = new JmsTransactionManager(cachingConnectionFactory);
		registry.put("jmsTransactionManager", transactionManager);
		
		// Default is SYNCHRONIZATION_NEVER - this seems to cause problems with propagation behaviour
		transactionManager.setTransactionSynchronization(JmsTransactionManager.SYNCHRONIZATION_ON_ACTUAL_TRANSACTION);

		// Propagation behaviour defines how transactions are handles across
		// boundaries / nested transactions etc (e.g. continue the transaction, create a new
		// transaction, don't use a transaction, etc)
		final SpringTransactionPolicy transactionPolicy = new SpringTransactionPolicy();
		transactionPolicy.setTransactionManager(transactionManager);
		transactionPolicy.setPropagationBehaviorName("PROPAGATION_NOT_SUPPORTED");
		registry.put("PROPAGATION_NOT_SUPPORTED", transactionPolicy);
		
		// Transactions are enabled for JMS
		// Also look at pre-fetch (default is 1000)
		// A setting of zero might help when using 'longer running' transactions
		final ActiveMQComponent jmsComponent = new ActiveMQComponent();
		jmsComponent.setConnectionFactory(cachingConnectionFactory);
		jmsComponent.setTransacted(true);
		jmsComponent.setTransactionManager(transactionManager);
		jmsComponent.setConcurrentConsumers(20);
		context.addComponent("jms", jmsComponent);
		
		// Transactions are not enabled on JMS2
		final ActiveMQComponent jms2Component = new ActiveMQComponent();
		jms2Component.setConnectionFactory(cachingConnectionFactory);
		jms2Component.setConcurrentConsumers(2);
		context.addComponent("jms2", jms2Component);
		
		context.addRoutes(new Routes());		
		context.start();
		consumerTemplate.start();
	}
	
	private class Routes extends RouteBuilder {
		@Override
		public void configure() throws Exception {
			final Set<String> inprogressIds = Collections.newSetFromMap(Maps.<String, Boolean>newConcurrentMap());
			
			from("jms:queue:documents?destination.consumer.prefetchSize=0")
				.errorHandler(new TransactionErrorHandlerBuilder()
					.asyncDelayedRedelivery()
					.maximumRedeliveries(2)
					.backOffMultiplier(2)
					.redeliveryDelay(2000)
					.log(LoggerFactory.getLogger(EndPartyReliabilitySenderApplication.class))
					.logExhausted(true)
				)
				.transacted("PROPAGATION_NOT_SUPPORTED")
				
				.setHeader(Exchange.CORRELATION_ID, simple("${bodyAs(String)}"))
				.setHeader(Exchange.FILE_NAME, simple("${header.CamelCorrelationId}/message"))
				.setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
				.setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
				.setHeader("ciao.message.type", constant("trunk-request"))
				.to("file://./target/docs")				
				.doTry()
					.multicast()
						.bean(inprogressIds, "add(${header.CamelCorrelationId})")
						.to("http4://localhost:8123/")
					.end()
					.setProperty(EndPartyReliabilityMessageExchange.CIAO_FUTURE, method(SettableFuture.class, "create"))
					.to("direct:trunk-request-response")
					.process(new EndPartyReliabilityMessageExchange.WaitForAck(40000)) // timeout is slightly higher than the corresponding value in the aggregate
					.validate().simple("${body.isComplete()}")
					.transform().simple("${body.getAckBody()}")
					.process(new AckProcessor())
				.endDoTry()
				.doCatch(HttpOperationFailedException.class)
					.process(new HttpErrorHandler())
				.doFinally()
					.bean(inprogressIds, "remove(${header.CamelCorrelationId})")
				;
			
			// Using a separate JMS component to ensure this route is not blocked
			// if all other consumer queue threads (on jms:) are waiting for ACK responses
			from("jms2:topic:document-ebxml-acks")
				// multiple threads may be running - only process each incoming ack once
				.idempotentConsumer(header("JMSMessageID"), new MemoryIdempotentRepository())
				
				// only handle IDs currently active in this process
				.filter(method(inprogressIds, "contains(${header.JMSCorrelationId})"))
				.setHeader(Exchange.CORRELATION_ID, header("JMSCorrelationId"))
				.setHeader("ciao.message.type", constant("trunk-ack"))
				.log("Incoming ebxml ack for ${header.CamelCorrelationId}")
				.to("direct:trunk-request-response")
				;
			
			// Correlate original requests with incoming acks
			// acks may be received with no corresponding open request (i.e.
			// the request originated in another process)
			from("direct:trunk-request-response")
				.aggregate(header(Exchange.CORRELATION_ID), new EndPartyReliabilityMessageExchange.AggregationStrategy())
					.completionPredicate(method(EndPartyReliabilityMessageExchange.class, "isComplete(${body})"))
					.completionTimeout(30000)
				.log("Completed aggregate trunk-request-response ${header.CamelCorrelationId}")
				.bean(EndPartyReliabilityMessageExchange.class, "notifyCompletion(${body})")
				;
		}
	}
	
	private class AckProcessor implements Processor {
		@Override
		public void process(final Exchange exchange) throws Exception {
			final String id = exchange.getIn().getHeader(Exchange.CORRELATION_ID, String.class);
			final String body = exchange.getIn().getBody(String.class);
			if ("ack".equalsIgnoreCase(body)) {
				LoggerFactory.getLogger(getClass()).info("ebXml send-ack received - id: " + id);
				exchange.getOut().copyFrom(exchange.getIn());
				exchange.getOut().setBody(body);
			} else if ("nack-failure".equals(body)) {
				LoggerFactory.getLogger(getClass()).info("ebXml send-nack (failure) received - id: " + id +
						" - will not retry");
				exchange.getOut().copyFrom(exchange.getIn());
				exchange.getOut().setBody(body);
				exchange.getOut().setFault(true); // Fault messages are not retried!
			} else {
				// Exceptions are caught by camel and retried!
				throw new Exception("ebXml send-nack received - id: " + id + " -> " + body);
			}
		}
	}
	
	private class HttpErrorHandler implements Processor {
		@Override
		public void process(final Exchange exchange) throws Exception {
			final HttpOperationFailedException exception = exchange.getException(HttpOperationFailedException.class);
			if (exception.getStatusCode() >= 500) {
				LoggerFactory.getLogger(getClass()).info("HTTP server error: " + exception.getStatusCode());
				// re-throw
				throw exception;
			}
			
			final String body = exception.getResponseBody();
			if ("Rejected".equals(body)) {
				LoggerFactory.getLogger(getClass()).info("HTTP client error (failure): " + exception.getStatusCode());
				// mark as fault message - do not rethrow
				exchange.getOut().setBody(body);
				exchange.getOut().setFault(true);
			} else {
				LoggerFactory.getLogger(getClass()).info("HTTP client error (retry): " + exception.getStatusCode());
				// re-throw
				throw exception;
			}
		}
	}
	
	public static void main(final String[] args) throws Exception {
		new EndPartyReliabilitySenderApplication();
		
		while (true) {
			Thread.sleep(1000);
		}
	}
}
