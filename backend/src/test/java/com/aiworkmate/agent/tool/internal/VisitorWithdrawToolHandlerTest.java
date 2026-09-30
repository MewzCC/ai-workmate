package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.VisitorToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisitorWithdrawToolHandlerTest {
    @Test
    void forwardsOnlyTheVersionBoundBookingOwnedByTheTrustedActor() throws Exception {
        VisitorToolPort port = mock(VisitorToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 25L, 1, "trace");
        var command = new VisitorToolPort.VersionCommand(31, 2);
        when(port.withdraw(context.actor(), command)).thenReturn(
                new VisitorToolPort.StatusResult(31, "WITHDRAWN", 3));

        var output = new VisitorWithdrawToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"bookingId":31,"version":2}
                """));

        assertThat(output.path("status").asText()).isEqualTo("WITHDRAWN");
        verify(port).withdraw(context.actor(), command);
    }
}
