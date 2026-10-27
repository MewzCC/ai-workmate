package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.aiworkmate.agent.registry.AgentApprovalApplicationReadToolDefinitions;
import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApprovalApplicationMineQueryToolHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ApprovalApplicationToolPort port = mock(ApprovalApplicationToolPort.class);
    private final TrustedToolContext context = new TrustedToolContext(9L, 7L, 20L, 30L, 1, "trace");

    @Test void queriesAllGenericFormsByDefaultWithTrustedActor() throws Exception {
        var query = new ApprovalApplicationToolPort.Query(null, null, 1, 20);
        when(port.mine(context.actor(), query)).thenReturn(new ToolPage<>(List.of(), 0, 1, 20));
        var output = new ApprovalApplicationMineQueryToolHandler(port, mapper).execute(context, mapper.readTree("{}"));
        assertThat(new ToolSchemaValidator().valid(mapper.readTree(
                AgentApprovalApplicationReadToolDefinitions.OUTPUT_SCHEMA), output)).isTrue();
        verify(port).mine(context.actor(), query);
    }

    @Test void appliesStatusFormAndPagination() throws Exception {
        var query = new ApprovalApplicationToolPort.Query("DRAFT", "travel", 2, 10);
        when(port.mine(context.actor(), query)).thenReturn(new ToolPage<>(List.of(), 0, 2, 10));
        new ApprovalApplicationMineQueryToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"status\":\"DRAFT\",\"formKey\":\"travel\",\"page\":2,\"size\":10}"));
        verify(port).mine(context.actor(), query);
    }

    @Test void invalidStatusOrFormNeverInvokesPort() throws Exception {
        var handler = new ApprovalApplicationMineQueryToolHandler(port, mapper);
        for (String json : new String[]{"{\"status\":\"ALL\"}", "{\"status\":null}",
                "{\"formKey\":false}", "{\"formKey\":\" \"}"}) {
            assertThatThrownBy(() -> handler.execute(context, mapper.readTree(json))).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(port);
    }

    @Test void revokedPermissionDoesNotFallback() throws Exception {
        var query = new ApprovalApplicationToolPort.Query(null, null, 1, 20);
        var denied = new BusinessException(ErrorCode.PERMISSION_DENIED);
        when(port.mine(context.actor(), query)).thenThrow(denied);
        assertThatThrownBy(() -> new ApprovalApplicationMineQueryToolHandler(port, mapper)
                .execute(context, mapper.readTree("{}"))).isSameAs(denied);
        verify(port).mine(context.actor(), query);
        verifyNoMoreInteractions(port);
    }
}
