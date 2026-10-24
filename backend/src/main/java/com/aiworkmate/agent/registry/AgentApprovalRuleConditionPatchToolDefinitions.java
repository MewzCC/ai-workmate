package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentApprovalRuleConditionPatchToolDefinitions {
    static final String INPUT = """
            {"type":"object","additionalProperties":false,"required":["ruleId","version","conditionNumber","value"],"properties":{"ruleId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":1,"maximum":2147483646},"conditionNumber":{"type":"integer","minimum":1,"maximum":10},"value":{"type":"string","minLength":1,"maxLength":120}}}
            """.strip();
    static final String OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["ruleId","version","status","conditionNumber","field","operator","value"],"properties":{"ruleId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":2,"maximum":2147483647},"status":{"type":"string","const":"DISABLED"},"conditionNumber":{"type":"integer","minimum":1,"maximum":10},"field":{"type":"string","enum":["amount","durationDays","department","employeeLevel","leaveType"]},"operator":{"type":"string","enum":["eq","ne","gt","gte","lt","lte","in"]},"value":{"type":"string","minLength":1,"maxLength":120}}}
            """.strip();
    @Bean
    ToolDefinition approvalRuleConditionPatchToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.APPROVAL_RULE_PATCH_DRAFT_CONDITION,
                "Patch one approval rule draft condition",
                "Changes only one existing condition value in one disabled tenant rule using an expected version and one-based condition number.",
                "Patch one disabled-rule condition value only. Preserve other conditions, logic, actions and metadata. Never accept raw JSON, change field/operator, enable or publish a rule.",
                mapper.readTree(INPUT), mapper.readTree(OUTPUT), ToolWriteProfile.NON_RETRYABLE_L1,
                Set.of("approval:manage"), OwnershipPolicy.FIXED_RESOURCE, 1, 4096, 15000);
    }
}
