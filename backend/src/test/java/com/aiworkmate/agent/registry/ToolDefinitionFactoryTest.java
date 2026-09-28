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
    void fixesTheIdempotentL1WriteSafetyProfile() throws Exception {
        ToolDefinition definition = ToolDefinitionFactory.singleWrite(
                ToolCode.LEAVE_CREATE_DRAFT, "name", "description", "purpose",
                mapper.readTree(ClosedToolSchemas.versionedResourceInput("applicationId")),
                mapper.readTree(ClosedToolSchemas.positiveIdInput("applicationId")),
                ToolWriteProfile.IDEMPOTENT_L1, Set.of("leave:create"), OwnershipPolicy.SELF,
                1, 4096, 10000);

        assertThat(definition.handlerVersion()).isEqualTo("1.0.0");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.permissionMode()).isEqualTo(PermissionMode.ALL);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.auditPolicy()).isEqualTo("FULL_WRITE_AUDIT");
    }

    @Test
    void exposesOnlyReviewedWritePolicyCombinations() {
        assertThat(ToolWriteProfile.values()).extracting(
                        ToolWriteProfile::riskLevel,
                        ToolWriteProfile::retryPolicy,
                        ToolWriteProfile::confirmationPolicy)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                RiskLevel.L1, RetryPolicy.BUSINESS_IDEMPOTENT, ConfirmationPolicy.EXPLICIT),
                        org.assertj.core.groups.Tuple.tuple(
                                RiskLevel.L1, RetryPolicy.NEVER, ConfirmationPolicy.EXPLICIT),
                        org.assertj.core.groups.Tuple.tuple(
                                RiskLevel.L2, RetryPolicy.NEVER, ConfirmationPolicy.SECONDARY));
    }

    @Test
    void rejectsMissingWriteProfile() throws Exception {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ToolDefinitionFactory.singleWrite(
                        ToolCode.LEAVE_WITHDRAW, "name", "description", "purpose",
                        mapper.readTree(ClosedToolSchemas.versionedResourceInput("applicationId")),
                        mapper.readTree(ClosedToolSchemas.positiveIdInput("applicationId")),
                        null, Set.of("leave:withdraw"), OwnershipPolicy.SELF,
                        1, 4096, 10000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Write profile is required");
    }
}
