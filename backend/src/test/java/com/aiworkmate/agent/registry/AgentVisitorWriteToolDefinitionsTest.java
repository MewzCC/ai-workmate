package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentVisitorWriteToolDefinitionsTest {
    @Test
    void visitorApplyIsOneConfirmedSelfOwnedWriteWithFrozenSchema() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentVisitorWriteToolDefinitions()
                .visitorApplyToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:c4bc3759bc77b4289d9391e14f018f6680234c4d1becbc0099bb207a434a877b");
        assertThat(definition.requiredPermissions()).containsExactly("visitor:create");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"visitorName":"访客甲","purpose":"项目交流","hostUserId":9,
                 "expectedVisitAt":"2026-09-20T09:00:00","partySize":2}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"visitorName":"访客甲","purpose":"项目交流","hostUserId":9,
                 "expectedVisitAt":"2026-09-20T09:00:00","partySize":2,"applicantUserId":7}
                """))).isFalse();
    }

    @Test
    void visitorCheckInIsOneConfirmedRelatedBookingWriteWithFrozenSchema() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentVisitorWriteToolDefinitions()
                .visitorCheckInToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:9c88700b29e31bc4bf30fb868ae13d46a7b17e977a0331b9d0d38e6ad88ae98c");
        assertThat(definition.requiredPermissions()).containsExactly("visitor:register");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"bookingId":31,"version":2,"remark":"已核验证件"}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"bookingId":31,"version":2,"operatorUserId":7}
                """))).isFalse();
    }

    @Test
    void visitorArrivalReusesTheBoundedVisitCommandWithFrozenResult() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentVisitorWriteToolDefinitions()
                .visitorMarkArrivedToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:d42a274c0b07852fbef93d734436875a289a0565c45c2beb92d547d27375a4e3");
        assertThat(definition.requiredPermissions()).containsExactly("visitor:register");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"bookingId":31,"version":3,"remark":"前台确认到访"}
                """))).isTrue();
        assertThat(definition.outputSchema().path("properties").path("status").path("const").asText())
                .isEqualTo("VISITED");
    }

    @Test
    void visitorLeaveReusesTheBoundedVisitCommandWithFrozenResult() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentVisitorWriteToolDefinitions()
                .visitorLeaveToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:1c372b6905396188d7f748da1ef6fae69168fc635a69c44e2f3e5e87003fe0c7");
        assertThat(definition.requiredPermissions()).containsExactly("visitor:register");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"bookingId":31,"version":4,"remark":"前台确认离场"}
                """))).isTrue();
        assertThat(definition.outputSchema().path("properties").path("status").path("const").asText())
                .isEqualTo("LEFT");
    }
}
