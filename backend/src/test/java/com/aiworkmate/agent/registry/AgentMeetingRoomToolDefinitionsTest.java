package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AgentMeetingRoomToolDefinitionsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentMeetingRoomToolDefinitions definitions = new AgentMeetingRoomToolDefinitions();
    private final ToolSchemaValidator validator = new ToolSchemaValidator();

    @Test void freezesPaginatedReadAndEitherExistingReadPermission() throws Exception {
        var tool = definitions.meetingRoomQueryToolDefinition(mapper);
        assertThat(tool.schemaHash()).isEqualTo("sha256:45bc82f82e3dab815be1eb58da17701d69d5ea5266af20f829099968b057d114");
        assertThat(tool.permissionMode()).isEqualTo(PermissionMode.ANY);
        assertThat(tool.requiredPermissions()).containsExactlyInAnyOrder("assets:read", "meeting:read:self");
        assertThat(tool.sideEffect()).isEqualTo(SideEffect.NONE);
        assertThat(validator.valid(tool.inputSchema(), mapper.readTree("{\"page\":2,\"size\":50}"))).isTrue();
        for (String json : new String[]{"{\"size\":51}", "{\"page\":10001}", "{\"tenantId\":2}", "{\"includeDeleted\":true}"}) {
            assertThat(validator.valid(tool.inputSchema(), mapper.readTree(json))).isFalse();
        }
    }
    @Test void createAndPatchUseNonRetryableConfirmedSingleWriteContracts() throws Exception {
        var create = definitions.meetingRoomCreateToolDefinition(mapper);
        var update = definitions.meetingRoomUpdateToolDefinition(mapper);
        assertThat(create.schemaHash()).isEqualTo("sha256:eb0a73f37a5acd02ff01ba4465f9c768a54d69d543dbbb63c8f0fb52fcb65539");
        assertThat(update.schemaHash()).isEqualTo("sha256:b4778abcb5352b23af844b4f466e619414ea72d9a818c5371830eb3c4fcde84b");
        for (var tool : java.util.List.of(create, update)) {
            assertThat(tool.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
            assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L1);
            assertThat(tool.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
            assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
            assertThat(tool.requiredPermissions()).containsExactly("meeting:write");
        }
        assertThat(validator.valid(create.inputSchema(), mapper.readTree("{\"code\":\"R1\",\"name\":\"Room\",\"capacity\":8,\"status\":\"OPEN\"}"))).isTrue();
        assertThat(validator.valid(update.inputSchema(), mapper.readTree("{\"roomId\":1,\"version\":0,\"name\":\"Renamed\"}"))).isTrue();
        assertThat(validator.valid(update.inputSchema(), mapper.readTree("{\"roomId\":1,\"version\":0,\"remark\":\"\"}"))).isTrue();
        for (String json : new String[]{"{\"roomId\":1,\"version\":0}", "{\"roomId\":1,\"version\":-1,\"name\":\"X\"}",
                "{\"roomId\":1,\"version\":0,\"status\":\"DELETED\"}", "{\"roomId\":1,\"version\":0,\"tenantId\":2}",
                "{\"roomId\":1,\"version\":0,\"name\":\"\"}", "{\"roomId\":1,\"version\":0,\"capacity\":10001}"}) {
            assertThat(validator.valid(update.inputSchema(), mapper.readTree(json))).isFalse();
        }
    }
}
