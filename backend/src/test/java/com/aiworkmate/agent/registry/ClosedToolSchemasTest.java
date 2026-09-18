package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClosedToolSchemasTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createsDeterministicClosedVersionedAndPositiveIdSchemas() throws Exception {
        var versioned = mapper.readTree(ClosedToolSchemas.versionedResourceInput("applicationId"));
        var positiveId = mapper.readTree(ClosedToolSchemas.positiveIdInput("notificationId"));

        assertThat(versioned.path("additionalProperties").asBoolean()).isFalse();
        assertThat(versioned.path("properties").path("version").path("maximum").asInt())
                .isEqualTo(Integer.MAX_VALUE - 1);
        assertThat(positiveId.path("required").get(0).asText()).isEqualTo("notificationId");
        assertThat(ClosedToolSchemas.versionedResourceInput("applicationId"))
                .isEqualTo(AgentLeaveWithdrawalToolDefinitions.INPUT_SCHEMA)
                .isEqualTo(AgentApprovalApplicationWriteToolDefinitions.SUBMIT_DRAFT_INPUT_SCHEMA);
        assertThat(ClosedToolSchemas.positiveIdInput("notificationId"))
                .isEqualTo(AgentNotificationWriteToolDefinitions.INPUT_SCHEMA);
    }

    @Test
    void rejectsPropertyNamesThatCouldChangeTheSchemaShape() {
        assertThatThrownBy(() -> ClosedToolSchemas.positiveIdInput("tenantId\" : {}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("property name");
        assertThatThrownBy(() -> ClosedToolSchemas.versionedResourceInput(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
