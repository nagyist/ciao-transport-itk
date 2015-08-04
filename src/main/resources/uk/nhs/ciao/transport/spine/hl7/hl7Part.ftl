<?xml version="1.0" encoding="UTF-8"?>
<${interactionId?xml} xmlns="urn:hl7-org:v3">
   <#if id??>
   <id root="${id?xml}"/>
   </#if>
   <#if creationTime??>
   <creationTime value="${creationTime?xml}"/>
   </#if>
   <versionCode code="V3NPfIT4.2.00"/>
   <#if interactionId??>
   <interactionId extension="${interactionId?xml}" root="2.16.840.1.113883.2.1.3.2.4.12"/>
   </#if>
   <processingCode code="P"/>
   <processingModeCode code="T"/>
   <acceptAckCode code="NE"/>
   <#if receiverAsid??>
   <communicationFunctionRcv>
      <device classCode="DEV" determinerCode="INSTANCE">
         <id extension="$receiverAsid?xml}" root="1.2.826.0.1285.0.2.0.107"/>
      </device>
   </communicationFunctionRcv>
   </#if>
   <#if senderAsid??>
   <communicationFunctionSnd>
      <device classCode="DEV" determinerCode="INSTANCE">
         <id extension="${senderAsid?xml}" root="1.2.826.0.1285.0.2.0.107"/>
      </device>
   </communicationFunctionSnd>
   </#if>
   
   <#if agentSystemAsid??>
   <ControlActEvent classCode="CACT" moodCode="EVN">
      <author1 typeCode="AUT">
         <AgentSystemSDS classCode="AGNT">
            <agentSystemSDS classCode="DEV" determinerCode="INSTANCE">
               <id extension="${agentSystemAsid?xml}" root="1.2.826.0.1285.0.2.0.107"/>
            </agentSystemSDS>
         </AgentSystemSDS>
      </author1>
	</ControlActEvent>
	</#if>
</${interactionId?xml}>
