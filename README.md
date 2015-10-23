# ciao-transport-itk

*CIP to transfer ITK messages over transports such as Spine or DTS/MESH*

## Introduction

The purpose of this module and associated CIPs is to handle the publishing of a business document using specifications from the [Interoperability Toolkit(ITK)](http://systems.hscic.gov.uk/interop/itk).

The ITK Core specification describe the `Distribution Envelope` and `Acknowledgement Framework` which wrap the business document and define the message flow associated with publishing the document. The `Distribution Envelope` is agnostic to the underlying transport mechanism, consequently the business document can be published using a number of concrete transports.

This results in a layered system of the form:
- **Business Message Layer** - e.g. [CDA documents](https://github.com/nhs-ciao/ciao-cda-builder/)
- **ITK Layer** - `Distribution Envelope` and `Acknowledgement Framework`
- **Transport Layer** - e.g. [Spine](http://systems.hscic.gov.uk/spine) or [DTS/MESH](http://systems.hscic.gov.uk/spine/DTS)

## Modules

To support the layered system outlined above, this component is split into multiple sub-modules.

### Libraries
- [ciao-transport-itk](ciao-transport-itk) - Provides models of key ITK-level components (`Distribution Envelope`, `Infrastructure Response`, ...), and the base classes/routes required by transport CIPs to handle the ITK-level message flows.

### Transport CIPs
- [ciao-transport-spine](ciao-transport-spine) - Publishes ITK messages over Spine.
- [ciao-transport-dts](ciao-transport-dts) - Publishes ITK messages over DTS/MESH.
