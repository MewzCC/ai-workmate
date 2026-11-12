package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentGenericApprovalTaskDetailToolDefinition {
    public static final String INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["taskId"],"properties":{"taskId":{"type":"integer","minimum":1},"fieldPage":{"type":"integer","minimum":1,"maximum":25}}}
            """.strip();
    public static final String OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["taskId","applicationId","applicantName","formName","title","status","version","instanceStatus","applicationStatus","overdue","fields","fieldsTotal","fieldPage","fieldPageSize","canApprove","canReject"],"properties":{"taskId":{"type":"integer","minimum":1},"applicationId":{"type":"integer","minimum":1},"applicantName":{"type":"string","maxLength":120},"formName":{"type":"string","maxLength":200},"title":{"type":"string","maxLength":200},"nodeName":{"type":"string","maxLength":200},"status":{"type":"string","enum":["PENDING","WAITING","APPROVED","REJECTED","CANCELLED"]},"version":{"type":"integer","minimum":0},"instanceStatus":{"type":"string","enum":["RUNNING","COMPLETED","CANCELLED"]},"applicationStatus":{"type":"string","enum":["PENDING","APPROVED","REJECTED","WITHDRAWN","CANCELLED"]},"submittedAt":{"type":"string","maxLength":32},"dueAt":{"type":"string","maxLength":32},"overdue":{"type":"boolean"},"fields":{"type":"array","maxItems":4,"items":{"type":"object","additionalProperties":false,"required":["name","label","type","values","redacted"],"properties":{"name":{"type":"string","minLength":1,"maxLength":120},"label":{"type":"string","minLength":1,"maxLength":200},"type":{"type":"string","maxLength":40},"values":{"type":"array","maxItems":20,"items":{"type":"string","maxLength":500}},"redacted":{"type":"boolean"}}}},"fieldsTotal":{"type":"integer","minimum":0,"maximum":100},"fieldPage":{"type":"integer","minimum":1,"maximum":25},"fieldPageSize":{"type":"integer","enum":[4]},"canApprove":{"type":"boolean"},"canReject":{"type":"boolean"},"blockedReason":{"type":"string","maxLength":500},"parentTaskId":{"type":"integer","minimum":1},"addSignMode":{"type":"string","enum":["PRE","POST"]},"canAddSign":{"type":"boolean"}}}
            """.strip();

    @Bean public ToolDefinition genericApprovalTaskDetailQueryToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(ToolCode.APPROVAL_GENERIC_TASK_DETAIL_QUERY, "Read an assigned generic approval task",
                "Reads one task assigned to the current user using the frozen submission schema and redacts sensitive fields.",
                "Read one existing generic approval task by taskId. Four fields per fieldPage, up to 25 pages. Task status and version belong to the current node; applicationStatus is the whole application. Never expose raw schema, rules, attachments or another assignee's task. Does not approve or reject.",
                mapper.readTree(INPUT_SCHEMA), mapper.readTree(OUTPUT_SCHEMA), Set.of("todo:read"),
                OwnershipPolicy.ASSIGNED_TO_SELF, 20, 262144, 15000);
    }
}
