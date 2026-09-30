package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaveRemindToolHandlerTest {
    @Test
    void forwardsOnlyOneVersionBoundOwnedApplication() throws Exception {
        LeaveToolPort port = mock(LeaveToolPort.class);
        ObjectMapper mapper = new ObjectMapper();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 25L, 1, "trace");
        when(port.remind(context.actor(), 41, 2)).thenReturn(
                new LeaveToolPort.StatusResult(41, "PENDING", 3));

        var output = new LeaveRemindToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"applicationId":41,"version":2}
                """));

        assertThat(output.path("status").asText()).isEqualTo("PENDING");
        verify(port).remind(context.actor(), 41, 2);
    }
}
