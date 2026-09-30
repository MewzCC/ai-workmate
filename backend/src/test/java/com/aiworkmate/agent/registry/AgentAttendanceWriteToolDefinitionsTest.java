package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentAttendanceWriteToolDefinitionsTest {
    @Test
    void clockIsConfirmedNonRetryableAndRejectsIdentityAndTimeInjection() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definition = new AgentAttendanceWriteToolDefinitions()
                .attendanceClockToolDefinition(mapper);
        var validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:dd02d5489088fddfdb73f510972bf86b047b75470e72ac4c8293758f908bdd86");
        assertThat(definition.requiredPermissions()).containsExactly("attendance:clock");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"clockType":"CLOCK_IN"}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"clockType":"CLOCK_IN","userId":99,"clockTime":"2026-09-30T09:00:00"}
                """))).isFalse();
    }

    @Test
    void reissueIsConfirmedIdempotentSelfOwnedAndRejectsIdentityInjection() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definition = new AgentAttendanceWriteToolDefinitions()
                .attendanceReissueApplyToolDefinition(mapper);
        var validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:bb3f7af3920e01290f14107d053425e8d986f988069eaa20031455ce49cce760");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"clockDate":"2026-09-14","clockType":"CLOCK_IN","reason":"忘记打卡"}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"clockDate":"2026-09-14","clockType":"CLOCK_IN","reason":"忘记打卡","userId":99}
                """))).isFalse();
    }
}
