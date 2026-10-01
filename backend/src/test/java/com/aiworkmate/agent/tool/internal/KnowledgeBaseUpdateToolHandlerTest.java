package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class KnowledgeBaseUpdateToolHandlerTest {
    @Test
    void mapsOnlyBoundedSettingsAndTrustedActor() throws Exception {
        var port = mock(KnowledgeToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(99L, 7L, 10L, 20L, 0, "trace");
        var command = new KnowledgeToolPort.UpdateBaseCommand(
                42L, "研发规范", "policy", "更新说明", 1200, 100, 8, 4);
        var updatedAt = LocalDateTime.of(2026, 10, 1, 21, 0);
        when(port.updateBase(context.actor(), command)).thenReturn(
                new KnowledgeToolPort.UpdateBaseResult(42L, "研发规范", "policy", "更新说明",
                        2, 8, 1200, 100, 8, 4, updatedAt));

        var result = new KnowledgeBaseUpdateToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"kbId":42,"name":"研发规范","icon":"policy","description":"更新说明",
                 "chunkSize":1200,"chunkOverlap":100,"denseTopK":8,"sparseTopK":4}
                """));

        assertThat(result.path("knowledgeBaseId").asLong()).isEqualTo(42L);
        assertThat(result.path("chunkSize").asInt()).isEqualTo(1200);
        assertThat(result.has("embeddingProvider")).isFalse();
        verify(port).updateBase(context.actor(), command);
        verifyNoMoreInteractions(port);
    }

    @Test
    void preservesProvidedEmptyDescriptionAndIconForExplicitClearing() throws Exception {
        var port = mock(KnowledgeToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(99L, 7L, 10L, 20L, 0, "trace");
        var command = new KnowledgeToolPort.UpdateBaseCommand(
                42L, null, "", "", null, null, null, null);
        when(port.updateBase(context.actor(), command)).thenReturn(
                new KnowledgeToolPort.UpdateBaseResult(42L, "研发规范", "knowledge-base", null,
                        2, 8, 1000, 120, 5, 5, LocalDateTime.of(2026, 10, 1, 21, 5)));

        new KnowledgeBaseUpdateToolHandler(port, mapper).execute(
                context, mapper.readTree("{\"kbId\":42,\"icon\":\"\",\"description\":\"\"}"));

        verify(port).updateBase(context.actor(), command);
    }
}
