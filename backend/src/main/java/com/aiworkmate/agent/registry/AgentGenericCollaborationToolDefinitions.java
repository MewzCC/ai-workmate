package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods=false)
public class AgentGenericCollaborationToolDefinitions {
    public static final String READ_INPUT="""
            {"type":"object","additionalProperties":false,"required":["taskId","purpose"],"properties":{"taskId":{"type":"integer","minimum":1},"purpose":{"type":"string","enum":["TRANSFER","CC","ADD_SIGN"]},"keyword":{"type":"string","maxLength":64},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();
    public static final String WRITE_INPUT="""
            {"type":"object","additionalProperties":false,"required":["taskId","targetUserId","version","reason"],"properties":{"taskId":{"type":"integer","minimum":1},"targetUserId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"reason":{"type":"string","minLength":1,"maxLength":500}}}
            """.strip();
    public static final String WRITE_OUTPUT="""
            {"type":"object","additionalProperties":false,"required":["applicationId","taskId","status","version","applicationStatus","action","originalAssigneeUserId","targetUserId","assigneeUserId"],"properties":{"applicationId":{"type":"integer","minimum":1},"taskId":{"type":"integer","minimum":1},"status":{"type":"string","const":"PENDING"},"version":{"type":"integer","minimum":1,"maximum":2147483647},"applicationStatus":{"type":"string","const":"PENDING"},"action":{"type":"string","enum":["TRANSFER","CC"]},"originalAssigneeUserId":{"type":"integer","minimum":1},"targetUserId":{"type":"integer","minimum":1},"assigneeUserId":{"type":"integer","minimum":1}}}
            """.strip();
    @Bean public ToolDefinition genericParticipants(ObjectMapper json)throws JsonProcessingException{
        return ToolDefinitionFactory.read(ToolCode.APPROVAL_GENERIC_TASK_PARTICIPANTS_QUERY,"Query generic task collaboration candidates",
                "Reads a bounded page of qualified candidates for one currently assigned generic approval task.",
                "Use the explicit taskId and TRANSFER, CC or ADD_SIGN purpose. ADD_SIGN excludes frozen and already added reviewers. Ask for an ambiguous recipient; never invent a user ID or use the leave-task candidate tool for generic tasks.",
                json.readTree(READ_INPUT),json.readTree(AgentApprovalMetadataReadToolDefinitions.OUTPUT_1),Set.of("approval:act","todo:read"),OwnershipPolicy.ASSIGNED_TO_SELF,50,16384,15000);
    }
    @Bean public ToolDefinition genericTransfer(ObjectMapper json)throws JsonProcessingException{return write(json,ToolCode.APPROVAL_GENERIC_TASK_TRANSFER,"Transfer one assigned generic task");}
    @Bean public ToolDefinition genericCopy(ObjectMapper json)throws JsonProcessingException{return write(json,ToolCode.APPROVAL_GENERIC_TASK_CC,"Copy one assigned generic task");}
    private ToolDefinition write(ObjectMapper json,ToolCode code,String name)throws JsonProcessingException{
        return ToolDefinitionFactory.singleWrite(code,name,"Performs one version-bound collaboration command for a currently assigned generic task.",
                "Require taskId, a verified recipient ID, task version and a nonblank user-supplied reason. Ask for missing or ambiguous values. Execute one command only after secondary confirmation. Transfer changes only this task assignment, preserving the frozen plan. CC creates a read-only internal notification without approval rights. Never use leave-task tools, batch, or retry an unknown write result.",
                json.readTree(WRITE_INPUT),json.readTree(output(code)),ToolWriteProfile.SECONDARY_L2,Set.of("approval:act","todo:read"),OwnershipPolicy.ASSIGNED_TO_SELF,1,8192,15000);
    }
    public static String output(ToolCode code){
        if(code!=ToolCode.APPROVAL_GENERIC_TASK_TRANSFER && code!=ToolCode.APPROVAL_GENERIC_TASK_CC)throw new IllegalArgumentException("Unsupported collaboration command");
        return WRITE_OUTPUT.replace("[\"TRANSFER\",\"CC\"]",code==ToolCode.APPROVAL_GENERIC_TASK_TRANSFER?"[\"TRANSFER\"]":"[\"CC\"]");
    }
}
