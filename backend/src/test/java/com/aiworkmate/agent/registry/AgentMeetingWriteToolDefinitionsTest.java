package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMeetingWriteToolDefinitionsTest {
    @Test
    void cancellationIsConfirmedSelfOwnedAndRejectsIdentityInjection() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definition = new AgentMeetingWriteToolDefinitions().meetingCancelToolDefinition(mapper);
        var validator = new ToolSchemaValidator();
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:c3ba189f1f0dbee9207312b103eeacc6c4dea6d695aa196a5a61740f560e3c2c");
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"bookingId\":18,\"version\":2}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"bookingId\":18,\"version\":2,\"userId\":99}"))).isFalse();
    }
    @Test
    void bookingDefinitionIsConfirmedIdempotentAndIdentityFree() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentMeetingWriteToolDefinitions().meetingBookToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:08abfb0571d3c4b1576423f7aae0849039062dccca5025efef23e9cb0e88f276");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"roomId":8,"title":"产品评审","startAt":"2026-09-20T10:00:00",
                 "endAt":"2026-09-20T11:00:00","attendeeCount":6}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"roomId":8,"title":"产品评审","startAt":"2026-09-20T10:00:00",
                 "endAt":"2026-09-20T11:00:00","attendeeCount":6,"userId":99}
                """))).isFalse();
    }
}
