package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class KnowledgeDocumentQueryToolHandlerTest {
    @Test
    void defaultsToBoundedPageAndUsesTrustedActor() throws Exception {
        var port = mock(KnowledgeToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(99L, 7L, 10L, 20L, 0, "trace");
        var query = new KnowledgeToolPort.DocumentQuery(5L, null, 1, 20);
        var now = LocalDateTime.of(2026, 10, 1, 21, 30);
        when(port.queryDocuments(context.actor(), query)).thenReturn(new KnowledgeToolPort.DocumentQueryResult(
                5L, List.of(new KnowledgeToolPort.DocumentItem(42L, "policy.txt", 128, "TEXT", 3,
                        "READY", now, now)), 1, 1, 20));

        var result = new KnowledgeDocumentQueryToolHandler(port, mapper).execute(
                context, mapper.readTree("{\"kbId\":5}"));

        assertThat(result.path("records").get(0).path("documentId").asLong()).isEqualTo(42L);
        assertThat(result.toString()).doesNotContain(
                "embeddingProvider", "embeddingModel", "contentHash", "errorMessage", "content");
        verify(port).queryDocuments(context.actor(), query);
        verifyNoMoreInteractions(port);
    }

    @Test
    void mapsOneRequestedDocumentAndBoundsPageSize() throws Exception {
        var port = mock(KnowledgeToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(99L, 7L, 10L, 20L, 0, "trace");
        var query = new KnowledgeToolPort.DocumentQuery(5L, 42L, 3, 20);
        when(port.queryDocuments(context.actor(), query)).thenReturn(
                new KnowledgeToolPort.DocumentQueryResult(5L, List.of(), 0, 3, 20));

        new KnowledgeDocumentQueryToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"kbId\":5,\"documentId\":42,\"page\":3,\"size\":200}"));

        verify(port).queryDocuments(context.actor(), query);
    }
}
