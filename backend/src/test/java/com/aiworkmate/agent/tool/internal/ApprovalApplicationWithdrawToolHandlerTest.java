package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApprovalApplicationWithdrawToolHandlerTest {
    private final ApprovalApplicationToolPort port = mock(ApprovalApplicationToolPort.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final TrustedToolContext context = new TrustedToolContext(91L, 7L, 10L, 20L, 1, "trace");

    @Test
    void mapsOnlyOwnedResourceCoordinatesAndExpectedVersion() throws Exception {
        when(port.withdraw(context.actor(), 30L, 3)).thenReturn(
                new ApprovalApplicationToolPort.WriteResult(30L, "expense", "WITHDRAWN", 4));

        var output = new ApprovalApplicationWithdrawToolHandler(port, mapper)
                .execute(context, mapper.readTree("{\"applicationId\":30,\"version\":3}"));

        assertThat(output.path("applicationId").asLong()).isEqualTo(30L);
        assertThat(output.path("status").asText()).isEqualTo("WITHDRAWN");
        assertThat(output.path("version").asInt()).isEqualTo(4);
        verify(port).withdraw(context.actor(), 30L, 3);
    }

    @Test
    void rejectsInvalidApplicationIdBeforeCallingPort() throws Exception {
        var handler = new ApprovalApplicationWithdrawToolHandler(port, mapper);

        assertThatThrownBy(() -> handler.execute(context,
                mapper.readTree("{\"applicationId\":0,\"version\":3}")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(port);
    }
}
