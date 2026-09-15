package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LeaveWithdrawToolHandlerTest {
    @Test
    void delegatesSingleWithdrawalWithTrustedActorAndExpectedVersion() throws Exception {
        var port = mock(LeaveToolPort.class);
        var mapper = new ObjectMapper();
        var context = new TrustedToolContext(1, 7, 10, 20, 1, "trace");
        when(port.withdraw(context.actor(), 30, 2))
                .thenReturn(new LeaveToolPort.WithdrawalResult(30, "WITHDRAWN", 3));
        var output = new LeaveWithdrawToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"applicationId\":30,\"version\":2}"));
        assertThat(output.path("status").asText()).isEqualTo("WITHDRAWN");
        verify(port).withdraw(context.actor(), 30, 2);
        verifyNoMoreInteractions(port);
    }
}
