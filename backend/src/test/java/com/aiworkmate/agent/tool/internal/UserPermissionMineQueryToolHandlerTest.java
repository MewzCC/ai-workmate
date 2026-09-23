package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.UserPermissionToolPort;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPermissionMineQueryToolHandlerTest {
    private final UserPermissionToolPort port = mock(UserPermissionToolPort.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final UserPermissionMineQueryToolHandler handler =
            new UserPermissionMineQueryToolHandler(port, mapper);
    private final TrustedToolContext context =
            new TrustedToolContext(9L, 7L, 3L, 4L, 0, "trace");

    @Test
    void usesOnlyTrustedActorAndBoundedArguments() throws Exception {
        var query = new UserPermissionToolPort.PermissionQuery("approval", 2, 50);
        var response = new UserPermissionToolPort.PermissionPage(
                "EMPLOYEE", List.of("EMPLOYEE"), List.of("SELF"), 8L,
                List.of("approval:create"), 1, 2, 50);
        when(port.mine(context.actor(), query)).thenReturn(response);

        var output = handler.execute(context, mapper.readTree(
                "{\"keyword\":\" approval \",\"page\":2,\"size\":500}"));

        assertThat(output.path("primaryRole").asText()).isEqualTo("EMPLOYEE");
        assertThat(output.path("permissions").get(0).asText()).isEqualTo("approval:create");
        assertThat(output.has("userId")).isFalse();
        assertThat(output.has("tenantId")).isFalse();
        verify(port).mine(context.actor(), query);
    }

    @Test
    void defaultsPaginationAndRejectsOversizedKeyword() throws Exception {
        var query = new UserPermissionToolPort.PermissionQuery(null, 1, 20);
        when(port.mine(context.actor(), query)).thenReturn(new UserPermissionToolPort.PermissionPage(
                "EMPLOYEE", List.of(), List.of(), 0L, List.of(), 0, 1, 20));

        handler.execute(context, mapper.readTree("{}"));
        verify(port).mine(context.actor(), query);

        String longKeyword = "x".repeat(81);
        assertThatThrownBy(() -> handler.execute(context,
                mapper.readTree("{\"keyword\":\"" + longKeyword + "\"}")))
                .isInstanceOf(BusinessException.class);
    }
}
