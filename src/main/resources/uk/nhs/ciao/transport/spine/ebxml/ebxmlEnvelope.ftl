<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
	xmlns:eb="http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"<#if body.manifest>
	xmlns:hl7ebxml="urn:hl7-org:transport/ebxml/DSTUv1.0"
	xmlns:xlink="http://www.w3.org/1999/xlink"</#if>>
	<soap:Header>
		<eb:MessageHeader eb:version="2.0" soap:mustUnderstand="1">
			<#if body.fromParty??>
			<eb:From>
				<eb:PartyId eb:type="urn:nhs:names:partyType:ocs+serviceInstance">${body.fromParty?xml}</eb:PartyId>
			</eb:From>
			</#if>
			<#if body.toParty??>
			<eb:To>
				<eb:PartyId eb:type="urn:nhs:names:partyType:ocs+serviceInstance">${body.toParty?xml}</eb:PartyId>
			</eb:To>
			</#if>
			
			<#if body.cpaId??>
			<eb:CPAId>${body.cpaId?xml}</eb:CPAId>
			</#if>
			<#if body.conversationId??>
			<eb:ConversationId>${body.conversationId?xml}</eb:ConversationId>
			</#if>
			<#if body.service??>
			<eb:Service>${body.service?xml}</eb:Service>
			</#if>
			<#if body.action??>
			<eb:Action>${body.action?xml}</eb:Action>
			</#if>

			<eb:MessageData>
				<#if body.messageData.messageId??>
				<eb:MessageId>${body.messageData.messageId?xml}</eb:MessageId>
				</#if>
				<#if body.messageData.timestamp??>
				<eb:Timestamp>${body.messageData.timestamp?xml}</eb:Timestamp>
				</#if>
				<#if body.messageData.refToMessageId??>
				<eb:RefToMessageId>${body.messageData.refToMessageId?xml}</eb:RefToMessageId>
				</#if>
			</eb:MessageData>
			
			<eb:duplicateElimination />
		</eb:MessageHeader>
		<#if body.acknowledgment>
		<eb:Acknowledgment eb:version="2.0" soap:mustUnderstand="1" soap:actor="urn:oasis:names:tc:ebxml-msg:actor:toPartyMSH">
			<#if body.messageData.timestamp??>
			<eb:Timestamp>${body.messageData.timestamp?xml}</eb:Timestamp>
			</#if>
			<#if body.messageData.refToMessageId??>
			<eb:RefToMessageId>${body.messageData.refToMessageId?xml}</eb:RefToMessageId>
			</#if>
			<#if body.fromParty??>
			<eb:From>
				<eb:PartyId eb:type="urn:nhs:names:partyType:ocs+serviceInstance">${body.fromParty?xml}</eb:PartyId>
			</eb:From>
			</#if>
		</eb:Acknowledgment>
		</#if>
		<#if body.SOAPFault>
		<eb:ErrorList <#if body.error.listId??>eb:id="${body.error.listId?xml}"</#if> eb:highestSeverity="Error" eb:version="2.0" soap:mustUnderstand="1">
			<eb:Error <#if body.error.id??>eb:id="${body.error.id?xml}"</#if> <#if body.error.code??>eb:errorCode="${body.error.code?xml}"</#if> <#if body.error.severity??>eb:severity="${body.error.severity?xml}"</#if> <#if body.error.codeContext??>eb:codeContext="${body.error.codeContext?xml}"</#if>>
				<#if body.error.description??>
				<eb:Description xml:lang="en-GB">${body.error.description?xml}</eb:Description>
				</#if>
			</eb:Error>
		</eb:ErrorList>
		</#if>
	</soap:Header>
	<#if body.manifest>
	<soap:Body>
		<eb:Manifest eb:version="2.0">
			<#list body.manifestReferences as reference>
			<eb:Reference <#if reference.href??>xlink:href="${reference.href?xml}"</#if>>
				<#if reference.hl7>
				<eb:Schema eb:location="http://www.nhsia.nhs.uk/schemas/HL7-Message.xsd" eb:version="1.0"/>
				</#if>
				<#if reference.description??>
				<eb:Description xml:lang="en">${reference.description?xml}</eb:Description>
				</#if>
				<#if reference.hl7>
				<hl7ebxml:Payload style="HL7" encoding="XML" version="3.0"/>
				</#if>
			</eb:Reference>
			</#list>
		</eb:Manifest>
	</soap:Body>
	<#else>
	<soap:Body />
	</#if>
</soap:Envelope>
