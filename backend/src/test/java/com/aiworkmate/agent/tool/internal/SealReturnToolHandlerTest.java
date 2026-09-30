package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.SealToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SealReturnToolHandlerTest {
    @Test
    void forwardsOnlyTheVersionBoundReturnToTheDomainPort() throws Exception {
        SealToolPort port = mock(SealToolPort.class);
        ObjectMapper mapper = new ObjectMapper();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 25L, 1, "trace");
        var command = new SealToolPort.ReturnCommand(41, 3, "印章已归还");
        when(port.returnSeal(context.actor(), command)).thenReturn(
                new SealToolPort.StatusResult(41, "RETURNED", 4));

        var output = new SealReturnToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"usageId":41,"version":3,"remark":"印章已归还"}
                """));

        assertThat(output.path("status").asText()).isEqualTo("RETURNED");
        verify(port).returnSeal(context.actor(), command);
    }
}
