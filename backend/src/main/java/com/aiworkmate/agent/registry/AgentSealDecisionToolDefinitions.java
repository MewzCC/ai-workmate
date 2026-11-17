package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods=false)
public class AgentSealDecisionToolDefinitions {
    @Bean public ToolDefinition sealApproveToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return definition(mapper,true);
    }
    @Bean public ToolDefinition sealRejectToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return definition(mapper,false);
    }
    private ToolDefinition definition(ObjectMapper mapper,boolean approved) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(approved?ToolCode.SEAL_APPROVE:ToolCode.SEAL_REJECT,
                approved?"Approve one assigned seal task":"Reject one assigned seal task",
                "Decides one assigned seal approval task using its task version, independently of the usage version.",
                "Use taskId and the task version from an actionable seal query, never the usage ID or usage version. Require secondary confirmation. Rejection needs a nonblank user-provided reason; ask when missing. The domain rechecks tenant, permission, assignment and task/usage/instance bindings. Approval only permits later seal use; it does not register actual use, return or archive a document. Never batch or replay an unknown write result.",
                mapper.readTree(approved?AssignedWorkflowDecisionSchemas.APPROVE_INPUT:AssignedWorkflowDecisionSchemas.REJECT_INPUT),
                mapper.readTree(AssignedWorkflowDecisionSchemas.output("SEAL_USAGE",approved)), ToolWriteProfile.SECONDARY_L2,
                Set.of("approval:act"), OwnershipPolicy.ASSIGNED_TO_SELF,1,8192,15000);
    }
}
