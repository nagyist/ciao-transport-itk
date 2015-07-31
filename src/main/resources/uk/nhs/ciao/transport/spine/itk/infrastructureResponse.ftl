<?xml version="1.0" encoding="utf-8"?>
<itk:InfrastructureResponse xmlns:itk="urn:nhs-itk:ns:201005"<#if result??> result="${result?xml}"</#if><#if timestamp??> timestamp="${timestamp?xml}"</#if><#if trackingIdRef??> trackingIdRef="${trackingIdRef?xml}"</#if><#if serviceRef??> serviceRef="${serviceRef?xml}"</#if>>
	<#if reportingIdentity??>
	<itk:reportingIdentity>
		<itk:id<#if reportingIdentity.uri??> uri="${reportingIdentity.uri?xml}"</#if><#if reportingIdentity.type??> type="${reportingIdentity.type?xml}"</#if> />
	</itk:reportingIdentity>
	</#if>
	<#if errors?size == 0>
	<itk:errors />
	<#else>
	<itk:errors>
		<#list errors as error>
			<itk:errorInfo>
				<#if error.id??>
				<itk:ErrorID>${error.id?xml}</itk:ErrorID>
				</#if>
				<#if error.code??>
				<itk:ErrorCode<#if error.codeSystem??> codeSystem="${error.codeSystem?xml}"</#if>>${error.code?xml}</itk:ErrorCode>
				</#if>
				<#if error.text??>
				<itk:ErrorText>${error.text?xml}</itk:ErrorText>
				</#if>
				<#if error.diagnosticText??>
				<itk:ErrorDiagnosticText>${error.diagnosticText?xml}</itk:ErrorDiagnosticText>
				</#if>
			</itk:errorInfo>
		</#list>
	</itk:errors>
	</#if>
</itk:InfrastructureResponse>
