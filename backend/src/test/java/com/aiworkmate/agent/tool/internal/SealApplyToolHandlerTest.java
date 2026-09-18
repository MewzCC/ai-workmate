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

class SealApplyToolHandlerTest {
    @Test
    void mapsClosedApplicationAndUsesStableOperationKey() throws Exception {
        SealToolPort port = mock(SealToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 25L, 1, "trace");
        var command = new SealToolPort.ApplicationCommand("OFFICIAL", "采购合同", "签约", 2);
        var operationKey = new ToolOperationKey("agent:10:25:seal.apply:v1");
        LocalDateTime submittedAt = LocalDateTime.of(2026, 9, 20, 9, 0);
        when(port.apply(context.actor(), command, operationKey)).thenReturn(
                new SealToolPort.ApplicationResult(31, "PENDING", 0, submittedAt));

        var output = new SealApplyToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"sealType":"OFFICIAL","documentTitle":"采购合同","usageReason":"签约","copies":2}
                """));

        assertThat(output.path("usageId").asLong()).isEqualTo(31);
        assertThat(output.path("status").asText()).isEqualTo("PENDING");
        verify(port).apply(context.actor(), command, operationKey);
    }
}
