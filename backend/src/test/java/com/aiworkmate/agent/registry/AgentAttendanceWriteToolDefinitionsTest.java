package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentAttendanceWriteToolDefinitionsTest {
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
