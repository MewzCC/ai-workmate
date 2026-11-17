package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods=false)
public class AgentGenericAddSignToolDefinition {
    public static final String INPUT_SCHEMA="""
            {"type":"object","additionalProperties":false,"required":["taskId","targetUserId","version","mode","reason"],"properties":{"taskId":{"type":"integer","minimum":1},"targetUserId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"mode":{"type":"string","enum":["PRE","POST"]},"reason":{"type":"string","minLength":1,"maxLength":500}}}
            """.strip();
    public static final String OUTPUT_SCHEMA="""
            {"type":"object","additionalProperties":false,"required":["applicationId","taskId","status","version","applicationStatus","action","mode","targetUserId","addedTaskId","addedTaskStatus","addedTaskVersion","activeTaskId"],"properties":{"applicationId":{"type":"integer","minimum":1},"taskId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","WAITING"]},"version":{"type":"integer","minimum":1,"maximum":2147483647},"applicationStatus":{"type":"string","const":"PENDING"},"action":{"type":"string","const":"ADD_SIGN"},"mode":{"type":"string","enum":["PRE","POST"]},"targetUserId":{"type":"integer","minimum":1},"addedTaskId":{"type":"integer","minimum":1},"addedTaskStatus":{"type":"string","enum":["PENDING","WAITING"]},"addedTaskVersion":{"type":"integer","const":0},"activeTaskId":{"type":"integer","minimum":1}}}
            """.strip();
    @Bean public ToolDefinition genericAddSign(ObjectMapper json)throws JsonProcessingException{
        return ToolDefinitionFactory.singleWrite(ToolCode.APPROVAL_GENERIC_TASK_ADD_SIGN,"Add one ordered generic reviewer",
                "Adds one qualified reviewer before or after a currently assigned frozen generic approval node, preserving the frozen flow and history.",
                "Require an existing root taskId, task version, explicit PRE or POST mode, verified recipient ID and a nonblank user-supplied reason. Read current details and use generic-task candidates with ADD_SIGN purpose when needed; ask for missing or ambiguous values. Never invent IDs or reasons. Only one L2 command after secondary confirmation. PRE suspends the root until the added reviewer approves; POST queues after the root. No nested add-sign, duplicate reviewer, next-node bypass, batch, external message or retry of an unknown result.",
                json.readTree(INPUT_SCHEMA),json.readTree(OUTPUT_SCHEMA),ToolWriteProfile.SECONDARY_L2,Set.of("approval:act","todo:read"),OwnershipPolicy.ASSIGNED_TO_SELF,1,8192,15000);
    }
}
