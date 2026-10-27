package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApprovalApplicationDetailQueryToolHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ApprovalApplicationToolPort port = mock(ApprovalApplicationToolPort.class);
    private final TrustedToolContext context = new TrustedToolContext(9L, 7L, 20L, 30L, 1, "trace");

    @Test void usesTrustedActorAndReturnsActualFields() throws Exception {
        var summary = new ApprovalApplicationToolPort.Summary(17, "travel", "Travel", "PENDING", 2,
                null, null, null, false, 0, true, false, false, false);
        when(port.detailMine(context.actor(), new ApprovalApplicationToolPort.DetailQuery(17, 1))).thenReturn(new ApprovalApplicationToolPort.Detail(summary, "SNAPSHOT", 3,
                List.of(new ApprovalApplicationToolPort.DetailField("reason", "Reason", "text", List.of("Client visit"), false)), 1, 1));
        var output = new ApprovalApplicationDetailQueryToolHandler(port, mapper)
                .execute(context, mapper.readTree("{\"applicationId\":17}"));
        assertThat(output.path("fields").get(0).path("values").get(0).asText()).isEqualTo("Client visit");
        assertThat(new com.aiworkmate.agent.gateway.ToolSchemaValidator().valid(mapper.readTree(
                com.aiworkmate.agent.registry.AgentApprovalApplicationDetailToolDefinition.OUTPUT_SCHEMA), output)).isTrue();
        verify(port).detailMine(context.actor(), new ApprovalApplicationToolPort.DetailQuery(17, 1));
    }

    @Test void invalidIdNeverCallsPort() throws Exception {
        var handler = new ApprovalApplicationDetailQueryToolHandler(port, mapper);
        for (String json : new String[]{"{}", "{\"applicationId\":0}", "{\"applicationId\":\"1\"}"}) {
            assertThatThrownBy(() -> handler.execute(context, mapper.readTree(json))).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(port);
    }

    @Test void domainDenialHasNoFallback() throws Exception {
        var denied = new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        when(port.detailMine(context.actor(), new ApprovalApplicationToolPort.DetailQuery(17, 1))).thenThrow(denied);
        assertThatThrownBy(() -> new ApprovalApplicationDetailQueryToolHandler(port, mapper)
                .execute(context, mapper.readTree("{\"applicationId\":17}"))).isSameAs(denied);
        verify(port).detailMine(context.actor(), new ApprovalApplicationToolPort.DetailQuery(17, 1));
        verifyNoMoreInteractions(port);
    }
}
