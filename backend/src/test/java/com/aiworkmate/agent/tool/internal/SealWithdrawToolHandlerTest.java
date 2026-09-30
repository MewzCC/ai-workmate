package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.SealToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SealWithdrawToolHandlerTest {
    @Test
    void forwardsOnlyTheVersionBoundUsageOwnedByTheTrustedActor() throws Exception {
        SealToolPort port = mock(SealToolPort.class);
        ObjectMapper mapper = new ObjectMapper();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 25L, 1, "trace");
        var command = new SealToolPort.VersionCommand(41, 2);
        when(port.withdraw(context.actor(), command)).thenReturn(
                new SealToolPort.StatusResult(41, "WITHDRAWN", 3));

        var output = new SealWithdrawToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"usageId":41,"version":2}
                """));

        assertThat(output.path("status").asText()).isEqualTo("WITHDRAWN");
        verify(port).withdraw(context.actor(), command);
    }
}
