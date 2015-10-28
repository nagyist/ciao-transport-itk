# ciao-transport-itk

*Library to provide models of ITK core components and base ITK-level Camel routes for concrete transport CIPs*

## Introduction

As outlined in the main project [README](../README.md), transferring documents using the [Interoperability Toolkit(ITK)](http://systems.hscic.gov.uk/interop/itk) specifications results in a layered system of:
- **Business Message Layer** - e.g. [CDA documents](https://github.com/nhs-ciao/ciao-cda-builder/)
- **ITK Layer** - `Distribution Envelope` and `Acknowledgement Framework`
- **Transport Layer** - e.g. [Spine](http://systems.hscic.gov.uk/spine) or [DTS/MESH](http://systems.hscic.gov.uk/spine/DTS)

This library sits at the `ITK Layer`. Other CIAO modules import this library and provide the additional transport-specific functionality to provide a CIP for a specific underlying transport.

## Models

The ITK core specifications define several XML message types which are used when sending messages and acknowledging receipt. The `ciao-transport-itk` library provides Java representations of these types, along with serialization to/from XML, and integration with Apache Camel's [type conversion system](http://camel.apache.org/type-converter.html).

### Distribution Envelope

The `Distribution Envelope` message is used to carry one or more payloads (of any kind), along with details of the sender, intended receiver, and a manifest describing the payload. In addition, the envelope provides instructions of how the message should be handled and whether any `Acknowledgement Framework` messages should be sent on receipt.

The `Distribution Envelope` is a **fundamental** component of the ITK-transports. Due to the layered protocol structure, the *payload-part* of the underlying transports (e.g. Spine multi-part data, DTS data file, etc) will actually an XML format `Distribution Envelope`. The *real business-level* payload, will in turn be wrapped inside the envelope.

**Java Classes:**
-	[DistributionEnvelope](src/main/java/uk/nhs/ciao/transport/itk/envelope/DistributionEnvelope.java) provides a bean-like representation of a distribution envelope.
-	[DistributionEnvelopeParser](src/main/java/uk/nhs/ciao/transport/itk/envelope/DistributionEnvelopeParser.java) parses an XML serialized distribution envelope to object form.
-	[DistributionEnvelopeSerializer](src/main/java/uk/nhs/ciao/transport/itk/envelope/DistributionEnvelopeSerializer.java) - serializes a distribution envelope object into XML.
-	[DistributionEnvelopeTypeConverter](src/main/java/uk/nhs/ciao/transport/itk/envelope/DistributionEnvelopeTypeConverter.java) - Integrates the distribution envelope parser and serializer with Camel.

**Creating, parsing and serializing distribution envelopes:**
```java
// Serializer/parser configuration (reusable objects)
DistributionEnvelopeParser parser = new DistributionEnvelopeParser();
DistributionEnvelopeSerializer serializer = new DistributionEnvelopeSerializer();

// Creating an envelope
DistributionEnvelope prototype = new DistributionEnvelope();
prototype.setService("service-id");
prototype.setSenderAddress(new Address("urn:nhs-uk:addressing:ods:sender-ods-code"));
prototype.addAddress(new Address("urn:nhs-uk:addressing:ods:receiver-ods-code"));

// Applying default values for non-specified fields (e.g. timestamp)
prototype.applyDefaults();

// Parsing an envelope
InputStream in = new FileInputStream("example-envelope.xml");
DistributionEnvelope envelope = parser.parse(in);

// Merging / copying properties between envelopes
boolean overwrite = false;
envelope.copyFrom(prototype, overwrite);

// Adding a payload
ManifestItem item = new ManifestItem();
item.setMimeType("application/xml");
item.setBase64(true);

boolean encodeBody = true;
envelope.addPayload(item, "<root>payload content</root>", encodeBody);

// Serializing an envelope
String xml = serializer.serialize(envelope);
```

**Camel type conversion:**
```java
public class ExampleRoute extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("jms:queue:incoming-messages")
            // the distribution envelope type converter is automatically registered in Camel
            .convertBodyTo(DistributionEnvelope.class)
            .log("Found distribution envelope from ${body.senderAddress} with trackingId: ${body.trackingId}")

            // Use / modify the distribution envelope in some way
            .bean(new DistributionEnvelopeProcessor())

            // Serialize the updated distribution envelope as XML
            .convertBodyTo(String.class)
            .log("Converted the distribution envelope to XML: ${body}")
        .end();
    }
}
```

### Infrastructure Response

The `Infrastructure Response` message is part of the ITK `Acknowledgement Framework`.

When a message is sent (wrapped in a `Distribution Envelope`), the sender can optionally request an infrastructure-level acknowledgement from the receiver. The receiver creates a corresponding `Infrastructure Response` message and sends it to the initial sender (wrapped in a `Distribution Envelope` as before).

An `Infrastructure Response` may represent either a success or a failure (for example if the `Distribution Envelope` was received and syntactically valid, but contains an invalid manifest).

**Java Classes:**
-	[InfrastructureResponse](src/main/java/uk/nhs/ciao/transport/itk/envelope/InfrastructureResponse.java) provides a bean-like representation of an infrastructure response.
-	[InfrastructureResponseParser](src/main/java/uk/nhs/ciao/transport/itk/envelope/InfrastructureResponseParser.java) parses an XML serialized infrastructure response to object form.
-	[InfrastructureResponseSerializer](src/main/java/uk/nhs/ciao/transport/itk/envelope/InfrastructureResponseSerializer.java) - serializes an infrastructure response object into XML.
-	[InfrastructureResponseTypeConverter](src/main/java/uk/nhs/ciao/transport/itk/envelope/InfrastructureResponseTypeConverter.java) - Integrates the infrastructure response parser and serializer with Camel.

**Creating, parsing and serializing infrastructure responses:**
```java
// Serializer/parser configuration (reusable objects)
InfrastructureResponseParser parser = new InfrastructureResponseParser();
InfrastructureResponseSerializer serializer = new InfrastructureResponseSerializer();

// Creating a response
InfrastructureResponse prototype = new InfrastructureResponse();
prototype.setTimestamp(System.currentTimeMillis());
prototype.setTrackingIdRef("1234567");
prototype.setServiceRef("original-service-ref");

// Parsing a response
InputStream in = new FileInputStream("example-response.xml");
InfrastructureResponse response = parser.parse(in);

// Serializing a response
String xml = serializer.serialize(response);
```

**Camel type conversion:**
```java
public class ExampleRoute extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("jms:queue:incoming-messages")
            // the infrastructure response type converter is automatically registered in Camel
            .convertBodyTo(InfrastructureResponse.class)
            .log("Found infrastructure response for trackingId: ${body.trackingIdRef} - isAck: ${body.isAck}")

            // Use / modify the response in some way
            .bean(new InfrastructureResponseProcessor())

            // Serialize the updated infrastructure response as XML
            .convertBodyTo(String.class)
            .log("Converted the infrastructure response to XML: ${body}")
        .end();
    }
}
```

### Business Acknowledgement

The `Business Acknowledgement` message is part of the ITK `Acknowledgement Framework`.

When a message is sent (wrapped in a `Distribution Envelope`), the sender can optionally request a business-level acknowledgement from the receiver. The receiver creates a corresponding `Business Acknowledgement` message and sends it to the initial sender (wrapped in a `Distribution Envelope` as before). This is in addition to potentially sending an `Infrastructure Response`.

The `Business Acknowledgement` message is expressed using specifications from [HL7](http://www.hl7.org/).

> *`ciao-itk-transport` does not currently provide a Java representation of the `Business Acknowledgement`. Instead, the key success/failure sections of the message are interpreted in Camel using XPath expressions.*

## Camel

### Message Flow

At the ITK-level, the message flow for sending a business message looks like:

	                         Sender            Receiver	
	Business Message            ------------------>
	Infrastructure Response     <------------------
	Business Acknowledgement    <------------------
	Infrastructure Response     ------------------>

*All messages are wrapped in a Distribution Envelope.*

### Routes

The Camel routes provided in `ciao-itk-transport` support the [message flow](#message-flow) outlined above.

The routes handle the wrapping and unwrapping of payloads into a Distribution Envelope, and the sending of infrastructure responses (if appropriate).

The actual sending and receiving of messages are left to the transport-specific CIP to implement. In particular, the messages emitted from the `ciao-itk-transport` camel routes contain just the ITK-level details. Resolution of the transport-specific address (probably from the ITK-level addressing details) needs to be performed by the transport-specific CIP.

Transport-specific CIPs integrate with the `ciao-itk-transport` via Camel route URIs (configurable in the CIP). Typically, these use the [direct](http://camel.apache.org/direct.html) URI scheme.

**Java Classes:**
-	[ItkDocumentSenderRoute](src/main/java/uk/nhs/ciao/transport/itk/route/ItkDocumentSenderRoute.java) - accepts an ITK business message, wraps it in a Distribution Envelope, and publishes it to a lower-level route for sending.
-	[DistributionEnvelopeSenderRoute](src/main/java/uk/nhs/ciao/transport/itk/route/DistributionEnvelopeSenderRoute.java) **(abstract)** - accepts a Distribution Envelope, converts it to a transport-specific form, and sends it (in a way suitable for the type of transport).
-	[DistributionEnvelopeReceiverRoute](src/main/java/uk/nhs/ciao/transport/itk/route/DistributionEnvelopeReceiverRoute.java) - accepts a Distribution Envelope, verifies it, and sends it to a higher-level route for consuming. Additionally, if an Infrastructure Response has been requested, one is generated and queued for sending.
-	[ItkMessageReceiverRoute](src/main/java/uk/nhs/ciao/transport/itk/route/ItkMessageReceiverRoute.java) - accepts an ITK message payload (technically still wrapped in a *validated* Distribution Envelope), and interprets/handles the payload. Currently the expected messages types are an Infrastructure Response or Business Acknowledgement, handling these message types results in a *document upload process event* to be emitted (for consumption by [ciao-docs-finalizer](https://github.com/nhs-ciao/ciao-docs-finalizer/)). 

The route classes typically provide the logic/glue required to move between layers in the system - e.g. turning a business message into a corresponding distribution envelope message.
