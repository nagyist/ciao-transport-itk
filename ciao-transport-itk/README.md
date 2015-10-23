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

**Java Classes:**
-	[DistributionEnvelope](src/main/java/uk/nhs/ciao/transport/itk/envelope/DistributionEnvelope.java) provides a bean-like representation of a distribution envelope.
-	[DistributionEnvelopeParser](src/main/java/uk/nhs/ciao/transport/itk/envelope/DistributionEnvelopeParser.java) parses an XML serialized distribution envelope to object form.
-	[DistributionEnvelopeSerializer](src/main/java/uk/nhs/ciao/transport/itk/envelope/DistributionEnvelopeSerializer.java) - serializes a distribution envelope object into XML.
-	[DistributionEnvelopeTypeConverter](src/main/java/uk/nhs/ciao/transport/itk/envelope/DistributionEnvelopeTypeConverter.java) - Integrates the distribution envelope parser and serializer with Camel.


### Infrastructure Response

**Java Classes:**
-	[InfrastructureResponse](src/main/java/uk/nhs/ciao/transport/itk/envelope/InfrastructureResponse.java) provides a bean-like representation of an infrastructure response.
-	[InfrastructureResponseParser](src/main/java/uk/nhs/ciao/transport/itk/envelope/InfrastructureResponseParser.java) parses an XML serialized infrastructure response to object form.
-	[InfrastructureResponseSerializer](src/main/java/uk/nhs/ciao/transport/itk/envelope/InfrastructureResponseSerializer.java) - serializes an infrastructure response object into XML.
-	[InfrastructureResponseTypeConverter](src/main/java/uk/nhs/ciao/transport/itk/envelope/InfrastructureResponseTypeConverter.java) - Integrates the infrastructure response parser and serializer with Camel.
