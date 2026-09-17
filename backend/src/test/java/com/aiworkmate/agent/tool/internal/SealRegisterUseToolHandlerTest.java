package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.SealToolPort;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SealRegisterUseToolHandlerTest {
    @Test
    void mapsClosedUseCommandAndUsesStableOperationKey() throws Exception {
        SealToolPort port = mock(SealToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 25L, 1, "trace");
        var command = new SealToolPort.UseCommand(41, 2, 2, "现场核对");
        var operationKey = new ToolOperationKey("agent:10:25:seal.registerUse:v1");
        LocalDateTime usedAt = LocalDateTime.of(2026, 9, 20, 10, 0);
        when(port.registerUse(context.actor(), command, operationKey)).thenReturn(
                new SealToolPort.UseResult(41, "USED", 3, 2, usedAt));

        var output = new SealRegisterUseToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"usageId":41,"version":2,"actualCopies":2,"remark":"现场核对"}
                """));

        assertThat(output.path("usageId").asLong()).isEqualTo(41);
        assertThat(output.path("status").asText()).isEqualTo("USED");
        assertThat(output.path("actualCopies").asInt()).isEqualTo(2);
        verify(port).registerUse(context.actor(), command, operationKey);
    }
}
