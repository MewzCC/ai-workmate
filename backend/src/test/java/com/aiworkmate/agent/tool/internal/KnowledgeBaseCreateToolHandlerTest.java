package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class KnowledgeBaseCreateToolHandlerTest {
    @Test
    void mapsOnlyBoundedBaseMetadataAndTrustedActor() throws Exception {
        var port = mock(KnowledgeToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(99L, 7L, 10L, 20L, 0, "trace");
        var command = new KnowledgeToolPort.CreateBaseCommand("研发制度", "book", "团队制度资料");
        var createdAt = LocalDateTime.of(2026, 10, 1, 20, 30);
        when(port.createBase(context.actor(), command)).thenReturn(
                new KnowledgeToolPort.CreateBaseResult(42L, "研发制度", "book", "团队制度资料", 0, 0, createdAt));

        var result = new KnowledgeBaseCreateToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"name\":\"研发制度\",\"icon\":\"book\",\"description\":\"团队制度资料\"}"));

        assertThat(result.path("knowledgeBaseId").asLong()).isEqualTo(42L);
        assertThat(result.path("documentCount").asLong()).isZero();
        verify(port).createBase(context.actor(), command);
        verifyNoMoreInteractions(port);
    }
}
