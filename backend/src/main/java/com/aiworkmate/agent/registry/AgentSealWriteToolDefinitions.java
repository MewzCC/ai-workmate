package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentSealWriteToolDefinitions {
    public static final String APPLY_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["sealType","documentTitle","usageReason","copies"],"properties":{"sealType":{"type":"string","enum":["OFFICIAL","CONTRACT","LEGAL","FINANCE","OTHER"]},"documentTitle":{"type":"string","minLength":1,"maxLength":200},"usageReason":{"type":"string","minLength":1,"maxLength":500},"copies":{"type":"integer","minimum":1,"maximum":1000}}}
            """.strip();
    public static final String APPLY_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["usageId","status","version","submittedAt"],"properties":{"usageId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","WITHDRAWN","USED","RETURNED"]},"version":{"type":"integer","minimum":0},"submittedAt":{"type":"string","format":"date-time"}}}
            """.strip();
    public static final String REGISTER_USE_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["usageId","version","actualCopies"],"properties":{"usageId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"actualCopies":{"type":"integer","minimum":1,"maximum":1000},"remark":{"type":"string","maxLength":500}}}
            """.strip();
    public static final String REGISTER_USE_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["usageId","status","version","actualCopies","usedAt"],"properties":{"usageId":{"type":"integer","minimum":1},"status":{"type":"string","const":"USED"},"version":{"type":"integer","minimum":1},"actualCopies":{"type":"integer","minimum":1,"maximum":1000},"usedAt":{"type":"string","format":"date-time"}}}
            """.strip();
    public static final String WITHDRAW_INPUT_SCHEMA = ClosedToolSchemas.versionedResourceInput("usageId");
    public static final String RETURN_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["usageId","version"],"properties":{"usageId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"remark":{"type":"string","maxLength":500}}}
            """.strip();
    public static final String WITHDRAW_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["usageId","status","version"],"properties":{"usageId":{"type":"integer","minimum":1},"status":{"type":"string","const":"WITHDRAWN"},"version":{"type":"integer","minimum":1}}}
            """.strip();
    public static final String RETURN_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["usageId","status","version"],"properties":{"usageId":{"type":"integer","minimum":1},"status":{"type":"string","const":"RETURNED"},"version":{"type":"integer","minimum":1}}}
            """.strip();

    @Bean
    ToolDefinition sealApplyToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.SEAL_APPLY, "Submit one seal usage application",
                "Creates one approval-bound seal usage application owned by the authenticated applicant.",
                "Submit one bounded seal usage application only after the applicant explicitly confirms it.",
                objectMapper.readTree(APPLY_INPUT_SCHEMA), objectMapper.readTree(APPLY_OUTPUT_SCHEMA),
                ToolWriteProfile.IDEMPOTENT_L1, Set.of("seal:create"), OwnershipPolicy.SELF,
                1, 8192, 15000);
    }

    @Bean
    ToolDefinition sealRegisterUseToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.SEAL_REGISTER_USE, "Register one approved seal use",
                "Registers actual use for one approved seal application owned by the authenticated applicant.",
                "Register one version-bound actual seal use only after secondary confirmation.",
                objectMapper.readTree(REGISTER_USE_INPUT_SCHEMA),
                objectMapper.readTree(REGISTER_USE_OUTPUT_SCHEMA),
                ToolWriteProfile.SECONDARY_L2, Set.of("seal:register"), OwnershipPolicy.SELF,
                1, 8192, 10000);
    }

    @Bean
    ToolDefinition sealWithdrawToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.SEAL_WITHDRAW, "Withdraw one pending seal application",
                "Withdraws one pending seal application owned by the authenticated applicant.",
                "Withdraw one version-bound pending seal application after explicit confirmation.",
                objectMapper.readTree(WITHDRAW_INPUT_SCHEMA), objectMapper.readTree(WITHDRAW_OUTPUT_SCHEMA),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("seal:withdraw"), OwnershipPolicy.SELF,
                1, 8192, 10000);
    }

    @Bean
    ToolDefinition sealReturnToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.SEAL_RETURN, "Register one seal return",
                "Registers return for one used seal application visible to the authenticated registrar.",
                "Return one version-bound used seal record after explicit confirmation.",
                objectMapper.readTree(RETURN_INPUT_SCHEMA), objectMapper.readTree(RETURN_OUTPUT_SCHEMA),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("seal:register"), OwnershipPolicy.TENANT_SCOPED,
                1, 8192, 10000);
    }
}
