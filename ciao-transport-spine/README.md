# ciao-transport-spine

*CIP to transport messages over Spine*


## Introduction

As outlined in the main project [README](../README.md), transferring documents using the [Interoperability Toolkit(ITK)](http://systems.hscic.gov.uk/interop/itk) specifications results in a layered system of:
- **Business Message Layer** - e.g. [CDA documents](https://github.com/nhs-ciao/ciao-cda-builder/)
- **ITK Layer** - `Distribution Envelope` and `Acknowledgement Framework`
- **Transport Layer** - e.g. [Spine](http://systems.hscic.gov.uk/spine) or [DTS/MESH](http://systems.hscic.gov.uk/spine/DTS)

This CIP includes the `ITK Layer` (by importing [ciao-transport-itk](../ciao-transport-itk)) and the `Transport Layer` by provding Spine-specific functionality.

## How it Works

In order to understand how this CIP sends documents over the Spine, we first need to understand the different protocols in use:

![Protocol Stack](./docs/ProtocolStack.gif)

If we were to consider the full set of activities to send a document over the Spine using the below protocols, the process would appear quite complex. By splitting the activities into the two main layers (ITK and ebXML), the processing required is much easier to understand.

The ITK layer includes the basic sending, coupled with a simple acknowledgement framework which allows the sender to request an Infrastructrue Acknowledgement (to confirm the ITK message has reached it's destination), and a Business Acknowledgement (to confirm "business receipt" - the exact meaning of which is specific to the type of document being sent).

The below activity diagram shows the high level activities involved in processing the overall message and ITK-layer interactions:

![ITK Activity Diagram](./docs/ITKActivityDiagram.gif)

Some of the individual boxes on the ITK diagram above represent the lower level ebXML interactions - these lower level protocol activities are shown in the below ebXML activity diagram.

The ebXML layer deals with sending these higher level ITK interactions over the Spine. It uses a multipart HTTP message as defined in the ebXML specification, and each individual ebXML message is Asynchronously acknowledged with a corresponding ebXML Acknowledgement (or error).

![ebXML Activity Diagram](./docs/ebXMLActivityDiagram.gif)

## Configuration

For further details of how ciao-configuration and Spring XML interact, please see [ciao-core](https://github.com/nhs-ciao/ciao-core).

### Spring XML

On application start-up, a series of Spring Framework XML files are used to construct the core application objects. The created objects include the main Camel context, input/output components, routes and any intermediate processors.

The configuration is split into multiple XML files, each covering a separate area of the application. These files are selectively included at runtime via CIAO properties, allowing alternative technologies and/or implementations to be chosen. Each imported XML file can support a different set of CIAO properties.

The Spring XML files are loaded from the classpath under the [META-INF/spring](src/main/resources/META-INF/spring) package.

**Core:**

-   `beans.xml` - The main configuration responsible for initialising properties, importing additional resources and starting Camel.

**Repositories:**

> An `IdempotentRepository' is configured to enable [multiple consumers](http://camel.apache.org competing-consumers.html) access the same folder concurrently.

- 'repository/memory.xml' - An in-memory implementation suitable for use when there is only a single consumer, or multiple-consumers are all contained within the same JVM instance.
- 'repository/hazelcast.xml' - A grid-based implementation backed by [Hazelcast](http://camel.apache.org/hazelcast-component.html). The component is hosted entirely within the JVM process and uses a combination of multicast and point-to-point networking to maintain a cross-server data grid.

**Processors:**

-   `processors/default.xml` - *Currently a NOOP*

**Messaging:**

-   `messaging/activemq.xml` - Configures ActiveMQ as the JMS implementation for input/output queues.
-   `messaging/activemq-embedded.xml` - Configures an internal embedded ActiveMQ as the JMS implementation for input/output queues. *(For use during development/testing)*

**Addressing:**

-	`addressing/static.xml` - Configures the CIP to resolve ITK to Spine addresses using the static values defined via the `staticJson.resourcePaths` property.
-	`addressing/sds.xml` - Configures the CIP to resolve ITK to Spine addresses using the Spine Directory Service(SDS). Additionally address caching (via Hazelcast) is enabled, and static values can also be supplied via the `staticJson.resourcePaths` property.

**Spine SSL:**
-	`ssl/vanilla.xml` - Configures the CIP to use standard non-TLS sockets for connecting to Spine.
-	`ssl/tls.xml` - Configures the CIP to use TLS-enabled sockets for connecting to Spine. This configuration requires a key store and a trust store to be specified via the `KEY_STORE` and `TRUST_STORE` properties.

### CIAO Properties

At runtime ciao-transport-spine uses the available CIAO properties to determine which Spring XML files to load, which Camel routes to create, and how individual routes and components should be wired.

**Camel Logging:**

-	`camel.log.mdc` - Enables/disables [Mapped Diagnostic Context](http://camel.apache.org/mdc-logging.html) in Camel. If enabled, additional Camel context properties will be made available to Log4J and Logstash. 
-	`camel.log.trace` - Enables/disables the [Tracer](http://camel.apache.org/tracer.html) interceptor for Camel routes.
-	`camel.log.debugStreams` - Enables/disables [debug logging of streaming messages](http://camel.apache.org/how-do-i-enable-streams-when-debug-logging-messages-in-camel.html) in Camel.

**Spring Configuration:**

-   `repositoryConfig` - Selects which repository configuration to load:
	`repositories/${repositoryConfig}.xml`
-   `processorConfig` - Selects which processor configuration to load:
	`processors/${processorConfig}.xml`
-   `messagingConfig` - Selects which messaging configuration to load:
	`messaging/${messagingConfig}.xml`
-   `addressingConfig` - Selects which addressing configuration to load:
	`addressing/${addressingConfig}.xml`
-   `sslConfig` - Selects which SSL configuration to load:
	`ssl/${sslConfig}.xml`

**Spine Configuration:**
- `spine.toUri` - URI for sending outgoing messages to Spine.
- `spine.fromUri` - URI of HTTP/HTTPS server for receiving incoming messages from Spine.
- `spine.replyUri` - URI of JMS topic for processing asynchronous Spine ebXml acknowledgements.
- `sds.url` - URI of the SDS LDAP server.
- `sds.authentication` - Type of LDAP authentication used when connecting to SDS.
- `sds.principal` - LDAP principal / user used when connecting to SDS.
- `sds.credentials` - LDAP credentials / password used when connecting to SDS.

> Spine and SDS connection URIs depend on the selected SSL configuration. If TLS is enabled, then the URIs should include the `https`scheme, otherwise `http` should be used.

**Distribution Envelope Configuration:**
- `senderItkService` - The ITK service added to outgoing distribution envelopes
- `senderODSCode` - The sender ODS code added to outgoing distribution envelopes
- `auditODSCode` - The audit ODS code added to outgoing distribution envelopes (if this property is not defined, `senderODSCode` is used).

**EbXml/HL7 Configuration:**
- `senderService` - The ebXml service added to outgoing ebXml messages and SOAPAction headers
- `senderAction` - The ebXml action added to outgoing ebXml messages and SOAPAction headers
- `senderPartyId` - The sender PartyId added to outgoing ebXml messages
- `senderAsid` - The sender ASID added to outgoing HL7 messages

**Queue Configuration:**
- `itkDocumentSenderQueue` - JMS queue for processing outgoing ITK documents
- `multipartMessageSenderQueue` - JMS queue for processing outgoing Spine multipart messages
- `multipartMessageResponseQueue` - JMS queue for processing incoming Spine multipart messages
- `distributionEnvelopeReceiverQueue` - JMS queue for processing incoming ITK Distribution Envelopes
- `itkMessageReceiverQueue` - JMS queue for processing incoming ITK messages

**Address Resolution Configuration:**
- `addressing.staticFiles` - A comma-separated list of static files which provide static JSON-encoded [SpineEndpointAddress](src/main/java/uk/nhs/ciao/transport/spine/address/SpineEndpointAddress.java) values.
- `addressing.sdsCacheUri` - Defines the Hazelcast distributed map used to cache resolved endpoint adddresses.

> Configuration of the cache (e.g. time to live, cache size) is specified in the `repositories\hazelcast.xml` spring file. 

**In-progress Folder:**
> Details of the in-progress folder structure are available in the `ciao-docs-finalizer` [state machine](https://github.com/nhs-ciao/ciao-docs-finalizer/blob/master/docs/state-machine.md) documentation.

> `ciao-docs-parser` provides the [InProgressFolderManagerRoute](https://github.com/nhs-ciao/ciao-docs-parser/blob/master/ciao-docs-parser-model/src/main/java/uk/nhs/ciao/docs/parser/route/InProgressFolderManagerRoute.java) class to support storing control and event files in the in-progress directory.

- `inProgressFolder` - Defines the root folder that *document upload process* events are written to.

### Example
```INI
# Camel logging
camel.log.mdc=true
camel.log.trace=false
camel.log.debugStreams=false

# Select which processor config to use (via dynamic spring imports)
processorConfig=default

# Select which idempotent repository config to use (via dynamic spring imports)
repositoryConfig=hazelcast
# repositoryConfig=memory

# Select which messaging config to use (via dynamic spring imports)
messagingConfig=activemq
#messagingConfig=activemq-embedded

# Select which addressing config to use (via dynamic spring imports)
addressingConfig=static
#addressingConfig=sds

# Select which ssl config to use (via dynamic spring imports)
sslConfig=vanilla

# ActiveMQ settings (if messagingConfig=activemq)
activemq.brokerURL=tcp://localhost:61616
activemq.userName=smx
activemq.password=smx

spine.toUri=http://localhost:8123/
spine.fromUri=jetty:http://localhost:8122/
spine.replyUri=jms2:topic:document-ebxml-acks

# Spine SSL settings
TRUST_STORE=/opt/keystores/SpineDEVCerts.keystore
TRUST_STORE_PW=password
KEY_STORE=/opt/keystores/SpineCiaoTest1.keystore
KEY_STORE_PW=password
KEY_PASSWORD=password

# Spine SDS settings
sds.url=ldap://localhost:1234
sds.authentication=simple
sds.principal=cn=Manager,dc=example,dc=com
sds.credentials=passw0rd

# Common JMS/ActiveMQ settings (if messagingConfig=activemq or activemq-embedded)
jms.concurrentConsumers=20
jms2.concurrentConsumers=2

# Hazelcast settings (if repositoryConfig=hazelcast)
hazelcast.group.name=ciao-transport-spine
hazelcast.group.password=ciao-transport-spine-pass
hazelcast.network.port=5701
hazelcast.network.join.multicast.group=224.2.2.3
hazelcast.network.join.multicast.port=54327

# Common addressing settings (if addressingConfig=static or sds)
addressing.staticFiles=

# SDS addressing settings (if addressingConfig=sds)
addressing.sdsCacheUri=hazelcast:map:spine-endpoint-addresses

senderPartyId=!REQUIRED!
senderAsid=!REQUIRED!
senderODSCode=!REQUIRED!
# auditODSCode=optional - defaults to senderODSCode

senderItkService=urn:nhs-itk:services:201005:sendDistEnvelope
senderService=urn:nhs:names:services:itk
senderAction=COPC_IN000001GB01

itkDocumentSenderQueue=cda-documents
multipartMessageSenderQueue=multipart-message-sender
multipartMessageResponseQueue=multipart-message-responses
distributionEnvelopeReceiverQueue=distribution-envelope-receiver
itkMessageReceiverQueue=itk-message-receiver

inProgressFolder=./in-progress
```

Building and Running
--------------------

To pull down the code, run:

	git clone https://github.com/nhs-ciao/ciao-transport-itk.git
	
You can then compile the module via:

    cd ciao-transport-itk-parent
	mvn clean install -P bin-archive

This will compile a number of related modules - the main CIP module for Spine is `ciao-transport-spine`, and the full binary archive (with dependencies) can be found at `ciao-transport-spine\target\ciao-transport-spine-{version}-bin.zip`. To run the CIP, unpack this zip to a directory of your choosing and follow the instructions in the README.txt.

The CIP requires access to various file system directories and network ports (dependent on the selected configuration):

**etcd**:
 -  Connects to: `localhost:2379`

**ActiveMQ**:
 -  Connects to: `localhost:61616`

**Hazelcast**:
 -  Multicast discovery: `224.2.2.3:54327`
 -  Listens on: `*:5701` (If port is already taken, the port number is incremented until a free port is found)

**Spine**:
 -	Connects to the HTTP/HTTPS server specified by `spine.toUri`
 -	Connects to the LDAP server specified by `sds.url`
 -	Listens to the interface/port specified by `spine.fromUri`

**Filesystem**:
 -  If etcd is not available, CIAO properties will be loaded from: `~/.ciao/`
 -  For key events in the document upload lifecycle, the CIP will write an event to the folder specified by the `inProgressFolder` property.
 -  If static addresses are enabled, the files specified by the `addressing.staticFiles` property will be read from the file system. Relative paths are resolved relative to CIP working directory.
 -  If SSL/TLS is enabled, the files specified by the `TRUST_STORE` and `KEY_STORE` properties will be read from the file system.
