package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.SealToolPort;
import com.aiworkmate.agent.tool.port.VisitorToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisitorAndSealQueryToolHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 20L, 1, "trace");

    @Test
    void visitorHandlerUsesTrustedActorAndCapsListSize() throws Exception {
        VisitorToolPort port = mock(VisitorToolPort.class);
        var query = new VisitorToolPort.Query(null, VisitorToolPort.Queue.PENDING, null, 2, 50);
        when(port.query(context.actor(), query)).thenReturn(new VisitorToolPort.Page(List.of(), 0, 2, 50));
        var output = new VisitorQueryToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"queue\":\"PENDING\",\"page\":2,\"size\":500}"));
        assertThat(output.at("/size").asInt()).isEqualTo(50);
        verify(port).query(context.actor(), query);
    }

    @Test
    void sealHandlerUsesOwnedDetailWithoutIdentityArguments() throws Exception {
        SealToolPort port = mock(SealToolPort.class);
        var query = new SealToolPort.Query(9L, SealToolPort.Queue.MINE, null, 1, 20);
        when(port.query(context.actor(), query)).thenReturn(new SealToolPort.Page(List.of(), 0, 1, 1));
        var output = new SealQueryToolHandler(port, mapper).execute(context, mapper.readTree("{\"usageId\":9}"));
        assertThat(output.toString()).doesNotContain("userId", "tenantId", "storage");
        verify(port).query(context.actor(), query);
    }
}
