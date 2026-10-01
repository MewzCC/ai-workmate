package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class KnowledgeDocumentReindexToolHandlerTest {
    @Test
    void mapsOneDocumentAndUsesTrustedActor() throws Exception {
        var port = mock(KnowledgeToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(99L, 7L, 10L, 20L, 0, "trace");
        var command = new KnowledgeToolPort.ReindexDocumentCommand(42L);
        var updatedAt = LocalDateTime.of(2026, 10, 1, 21, 50);
        when(port.reindexDocument(context.actor(), command)).thenReturn(
                new KnowledgeToolPort.ReindexDocumentResult(42L, "policy.txt", "READY", 3, updatedAt));

        var result = new KnowledgeDocumentReindexToolHandler(port, mapper).execute(
                context, mapper.readTree("{\"documentId\":42}"));

        assertThat(result.path("documentId").asLong()).isEqualTo(42L);
        assertThat(result.path("status").asText()).isEqualTo("READY");
        assertThat(result.toString()).doesNotContain("embeddingProvider", "embeddingModel");
        verify(port).reindexDocument(context.actor(), command);
        verifyNoMoreInteractions(port);
    }
}
