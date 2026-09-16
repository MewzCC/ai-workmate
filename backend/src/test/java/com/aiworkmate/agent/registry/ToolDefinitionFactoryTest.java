package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ToolDefinitionFactoryTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fixesTheReadOnlySafetyProfile() throws Exception {
        ToolDefinition definition = ToolDefinitionFactory.read(
                ToolCode.TODO_QUERY, "name", "description", "purpose",
                mapper.readTree(ClosedToolSchemas.positiveIdInput("taskId")),
                mapper.readTree(ClosedToolSchemas.positiveIdInput("taskId")),
                Set.of("todo:read"), OwnershipPolicy.ASSIGNED_TO_SELF,
                20, 8192, 5000);

        assertThat(definition.handlerVersion()).isEqualTo("1.0.0");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L0);
        assertThat(definition.permissionMode()).isEqualTo(PermissionMode.ALL);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.READ_ONLY_SAFE);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.NONE);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.NONE);
        assertThat(definition.auditPolicy()).isEqualTo("HASHED_ARGS_RESULT");
    }

    @Test
    void fixesTheSingleWriteSafetyProfileWhileKeepingRiskAndConfirmationExplicit() throws Exception {
        ToolDefinition definition = ToolDefinitionFactory.singleWrite(
                ToolCode.LEAVE_WITHDRAW, "name", "description", "purpose",
                mapper.readTree(ClosedToolSchemas.versionedResourceInput("applicationId")),
                mapper.readTree(ClosedToolSchemas.positiveIdInput("applicationId")),
                RiskLevel.L1, Set.of("leave:withdraw"), OwnershipPolicy.SELF,
                RetryPolicy.NEVER, ConfirmationPolicy.EXPLICIT,
                1, 4096, 10000);

        assertThat(definition.handlerVersion()).isEqualTo("1.0.0");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.permissionMode()).isEqualTo(PermissionMode.ALL);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.auditPolicy()).isEqualTo("FULL_WRITE_AUDIT");
    }
}
