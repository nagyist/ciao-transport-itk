--${body.mimeBoundary}
Content-Id: ${body.ebxmlContentId}
Content-Type: text/xml
Content-Transfer-Encoding: 8bit

<?xml version="1.0" encoding="UTF-8"?>
<SOAP:Envelope xmlns:xsi="http://www.w3c.org/2001/XML-Schema-Instance" xmlns:SOAP="http://schemas.xmlsoap.org/soap/envelope/" xmlns:eb="http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd" xmlns:hl7ebxml="urn:hl7-org:transport/ebxml/DSTUv1.0" xmlns:xlink="http://www.w3.org/1999/xlink">
	<SOAP:Header>
		<eb:MessageHeader SOAP:mustUnderstand="1" eb:version="2.0">
			<eb:From>
				<eb:PartyId eb:type="urn:nhs:names:partyType:ocs+serviceInstance">AAA-123456<!-- Sender Party Key - Config --></eb:PartyId>
			</eb:From>
			<eb:To>
				<eb:PartyId eb:type="urn:nhs:names:partyType:ocs+serviceInstance">BBB-654321<!-- Receiver Party Key - From SDS --></eb:PartyId>
			</eb:To>
			<eb:CPAId>S3024519A3110234<!-- From SDS NHS entry (nhsMhsCPAId) for the recipient --></eb:CPAId>
			<eb:ConversationId>${body.ebxmlCorrelationId}</eb:ConversationId><!-- ideally this should stay consistent across the ITK interactions -->
			<eb:Service>urn:nhs:names:services:itk</eb:Service>
			<eb:Action>COPC_IN000001GB01<!-- Take from Config (interactionid) --></eb:Action>
			<eb:MessageData>
				<eb:MessageId>${body.ebxmlCorrelationId}</eb:MessageId>
				<eb:Timestamp>${body.creationTime?string["yyyy-MM-dd'T'HH:mm:ss"]}</eb:Timestamp><!-- Note: This is UTC (with a Z) -->
			</eb:MessageData>
			<eb:DuplicateElimination/>
		</eb:MessageHeader>
		<eb:AckRequested eb:version="2.0" SOAP:mustUnderstand="1" SOAP:actor="urn:oasis:names:tc:ebxml-msg:actor:toPartyMSH" eb:signed="false"/>
	</SOAP:Header>
	<SOAP:Body>
		<eb:Manifest eb:version="2.0">
			<eb:Reference xlink:href="cid:${body.hl7ContentId}">
				<eb:Schema eb:location="http://www.nhsia.nhs.uk/schemas/HL7-Message.xsd" eb:version="1.0"/>
				<eb:Description xml:lang="en">HL7 payload</eb:Description>
				<hl7ebxml:Payload style="HL7" encoding="XML" version="3.0"/>
			</eb:Reference>
			<eb:Reference xlink:href="cid:${body.itkContentId}">
				<eb:Description xml:lang="en">ITK Trunk Message</eb:Description>
			</eb:Reference>
		</eb:Manifest>
	</SOAP:Body>
</SOAP:Envelope>

--${body.mimeBoundary}
Content-Id: <${body.hl7ContentId}>
Content-Type: application/xml; charset=UTF-8
Content-Transfer-Encoding: 8bit

<?xml version="1.0" encoding="UTF-8"?>
<COPC_IN000001GB01 xmlns="urn:hl7-org:v3"><!-- Take from Config (interactionid) -->
   <id root="${body.hl7RootId}"/>
   <creationTime value="${body.creationTime?string["yyyyMMddHHmmss"]}"/><!-- Note: This is local time -->
   <versionCode code="V3NPfIT4.2.00"/>
   <interactionId extension="COPC_IN000001GB01" root="2.16.840.1.113883.2.1.3.2.4.12"/><!-- Take from Config (interactionid) -->
   <processingCode code="P"/>
   <processingModeCode code="T"/>
   <acceptAckCode code="NE"/>
   <communicationFunctionRcv>
      <device classCode="DEV" determinerCode="INSTANCE">
         <id extension="000000000000" root="1.2.826.0.1285.0.2.0.107"/><!-- Recipient ASID (from SDS) -->
      </device>
   </communicationFunctionRcv>
   <communicationFunctionSnd>
      <device classCode="DEV" determinerCode="INSTANCE">
         <id extension="866971180017" root="1.2.826.0.1285.0.2.0.107"/><!-- Sender ASID (Config) -->
      </device>
   </communicationFunctionSnd>
   <ControlActEvent classCode="CACT" moodCode="EVN">
      <!--<author typeCode="AUT">
         <AgentPersonSDS classCode="AGNT">
            <id extension="012345678901" root="1.2.826.0.1285.0.2.0.67"/>
            <agentPersonSDS classCode="PSN" determinerCode="INSTANCE">
               <id extension="687227875014" root="1.2.826.0.1285.0.2.0.65"/>
            </agentPersonSDS>
            <part typeCode="PART">
               <partSDSRole classCode="ROL">
                  <id extension="S0080:G0450:R5080" root="1.2.826.0.1285.0.2.1.104"/>
               </partSDSRole>
            </part>
         </AgentPersonSDS>
      </author>-->

      <author1 typeCode="AUT">
         <AgentSystemSDS classCode="AGNT">
            <agentSystemSDS classCode="DEV" determinerCode="INSTANCE">
               <id extension="866971180017" root="1.2.826.0.1285.0.2.0.107"/><!-- Sender ASID (Config) -->
            </agentSystemSDS>
         </AgentSystemSDS>
      </author1>
	</ControlActEvent>
</COPC_IN000001GB01><!-- Take from Config (interactionid) -->

--${body.mimeBoundary}
Content-Id: <${body.itkContentId}>
Content-Type: text/xml
Content-Transfer-Encoding: 8bit


<itk:DistributionEnvelope xmlns:itk="urn:nhs-itk:ns:201005" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
	<itk:header service="urn:nhs-itk:services:201005:sendDistEnvelope" trackingid="${body.itkCorrelationId}">
		<itk:addresslist>
			<itk:address uri="urn:nhs-uk:addressing:ods:BBB"/><!-- End bit is ODS code of receiver (from Document/PDS Enricher) -->
		</itk:addresslist>
		<itk:auditIdentity>
			<itk:id uri="urn:nhs-uk:identity:ods:AAA"/><!-- Our ODS code (Config) -->
		</itk:auditIdentity>
		<itk:manifest count="1">
			<itk:manifestitem mimetype="text/xml" id="uuid_${body.itkDocumentId}" profileid="urn:nhs-en:profile:eDischargeInpatientDischargeSummary-v1-0"/><!-- From static enricher -->
		</itk:manifest>
		<itk:senderAddress uri="urn:nhs-uk:addressing:ods:AAA"/><!-- End bit (AAA) is ODS code of sender (from Document/PDS Enricher) -->
		<itk:handlingSpecification>
			<itk:spec value="true" key="urn:nhs-itk:ns:201005:ackrequested"/>
			<itk:spec value="urn:nhs-itk:interaction:primaryRecipienteDischargeInpatientDischargeSummaryDocument-v1-0" key="urn:nhs-itk:ns:201005:interaction"/>
				<!-- Value above is from static enricher -->
		</itk:handlingSpecification>
	</itk:header>
	<itk:payloads count="1">
		<itk:payload id="uuid_${body.itkDocumentId}">${body.itkDocumentBody}</itk:payload>
	</itk:payloads>
</itk:DistributionEnvelope>

--${body.mimeBoundary}--
