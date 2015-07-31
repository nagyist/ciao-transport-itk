<?xml version="1.0" encoding="utf-8"?>
<itk:DistributionEnvelope xmlns:itk="urn:nhs-itk:ns:201005" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
	<itk:header<#if service??> service="${service?xml}"</#if><#if trackingId??> trackingid="${trackingId?xml}"</#if>>
		<#if addresses?size &gt; 0>
		<itk:addresslist>
			<#list addresses as address>
			<itk:address<#if address.uri??> uri="${address.uri?xml}"</#if><#if address.type??> type="${address.type?xml}"</#if> />
			</#list>
		</itk:addresslist>
		</#if>
		<#if auditIdentity??>
		<itk:auditIdentity>
			<itk:id<#if auditIdentity.uri??> uri="${auditIdentity.uri?xml}"</#if><#if auditIdentity.type??> type="${auditIdentity.type?xml}"</#if> />
		</itk:auditIdentity>
		</#if>
		<#if manifestItems?size &gt; 0>
		<itk:manifest count="${manifestItems?size?c}">
			<#list manifestItems as manifestItem>
			<itk:manifestitem<#if manifestItem.mimeType??> mimetype="${manifestItem.mimeType?xml}"</#if><#if manifestItem.id??> id="${manifestItem.id?xml}"</#if><#if manifestItem.profileId??> profileid="${manifestItem.profileId?xml}"</#if><#if manifestItem.base64> base64="true"</#if><#if manifestItem.compressed> compressed="true"</#if><#if manifestItem.encrypted> encrypted="true"</#if> />
			</#list>
		</itk:manifest>
		</#if>
		<#if senderAddress??>
		<itk:senderAddress<#if senderAddress.uri??> uri="${senderAddress.uri?xml}"</#if><#if senderAddress.type??> type="${senderAddress.type?xml}"</#if> />
		</#if>
		<#if handlingSpec.keys?size &gt; 0>
		<itk:handlingSpecification>
			<#list handlingSpec.keys as key>
			<itk:spec value="${handlingSpec.entries[key]?xml}" key="${key?xml}" />
			</#list>
		</itk:handlingSpecification>
		</#if>
	</itk:header>
	<#if payloads?size &gt; 0>
	<itk:payloads count="${payloads?size?c}">
		<#list payloads as payload>
		<itk:payload<#if payload.id??> id="${payload.id?xml}"</#if>><#if payload.body??>${payload.body}</#if></itk:payload>
		</#list>
	</itk:payloads>
	</#if>
</itk:DistributionEnvelope>
