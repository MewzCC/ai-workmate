package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaveSubmitToolHandlerTest {

    @Test
    void submitsVersionBoundDraftUsingTrustedUserAndTaskEvidence() throws Exception {
        LeaveToolPort service = mock(LeaveToolPort.class);
        ObjectMapper objectMapper = new ObjectMapper();
        LeaveSubmitToolHandler handler = new LeaveSubmitToolHandler(service, objectMapper);
        TrustedToolContext context = new TrustedToolContext(91L, 7L, 88L, 99L, 0, "trace");
        when(service.submit(context.actor(), 10L, 3))
                .thenReturn(application());

        var output = handler.execute(context,
                objectMapper.readTree("{\"applicationId\":10,\"version\":3}"));

        assertThat(output.toString()).isEqualTo("{\"applicationId\":10,\"status\":\"PENDING\",\"version\":4}");
        verify(service).submit(context.actor(), 10L, 3);
    }

    private LeaveToolPort.WriteResult application() {
        return new LeaveToolPort.WriteResult(10L, "PENDING", 4, 30L);
    }
}
