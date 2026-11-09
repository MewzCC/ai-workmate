package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods=false)
public class AgentVisitorDecisionToolDefinitions {
    @Bean public ToolDefinition visitorApproveToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return definition(mapper,true);
    }
    @Bean public ToolDefinition visitorRejectToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return definition(mapper,false);
    }
    private ToolDefinition definition(ObjectMapper mapper,boolean approved) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(approved?ToolCode.VISITOR_APPROVE:ToolCode.VISITOR_REJECT,
                approved?"Approve one assigned visitor task":"Reject one assigned visitor task",
                "Decides one assigned visitor approval task using its task version, independently of the booking version.",
                "Use taskId and the task version from an actionable visitor query, never the booking ID or booking version. Require secondary confirmation. Rejection needs a nonblank user-provided reason; ask when missing. The domain rechecks tenant, permission, assignment and task/booking/instance bindings. Approval is permission to visit, not check-in, arrival or departure. Never batch or replay an unknown write result.",
                mapper.readTree(approved?AssignedWorkflowDecisionSchemas.APPROVE_INPUT:AssignedWorkflowDecisionSchemas.REJECT_INPUT),
                mapper.readTree(AssignedWorkflowDecisionSchemas.output("VISITOR_BOOKING",approved)), ToolWriteProfile.SECONDARY_L2,
                Set.of("approval:act"), OwnershipPolicy.ASSIGNED_TO_SELF,1,8192,15000);
    }
}
