package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class KnowledgeBaseQueryToolHandlerTest {
    @Test
    void defaultsToBoundedListAndUsesTrustedActor() throws Exception {
        var port = mock(KnowledgeToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(99L, 7L, 10L, 20L, 0, "trace");
        var query = new KnowledgeToolPort.BaseQuery(null, 20);
        var now = LocalDateTime.of(2026, 10, 1, 20, 50);
        when(port.queryBases(context.actor(), query)).thenReturn(new KnowledgeToolPort.BaseQueryResult(List.of(
                new KnowledgeToolPort.BaseItem(42L, "研发制度", "book", null, 2, 8, now, now))));

        var result = new KnowledgeBaseQueryToolHandler(port, mapper).execute(context, mapper.readTree("{}"));

        assertThat(result.path("items").get(0).path("knowledgeBaseId").asLong()).isEqualTo(42L);
        assertThat(result.toString()).doesNotContain("embeddingProvider", "embeddingModel", "rerankModel");
        verify(port).queryBases(context.actor(), query);
        verifyNoMoreInteractions(port);
    }

    @Test
    void mapsOneRequestedOwnedKnowledgeBase() throws Exception {
        var port = mock(KnowledgeToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(99L, 7L, 10L, 20L, 0, "trace");
        var query = new KnowledgeToolPort.BaseQuery(42L, 50);
        when(port.queryBases(context.actor(), query)).thenReturn(new KnowledgeToolPort.BaseQueryResult(List.of()));

        new KnowledgeBaseQueryToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"kbId\":42,\"limit\":50}"));

        verify(port).queryBases(context.actor(), query);
    }
}
