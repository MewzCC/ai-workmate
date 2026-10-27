package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class KnowledgeDocumentCreateTextToolHandlerTest {
    @Test
    void mapsOnlyBoundedTextCommandAndTrustedActor() throws Exception {
        var port = mock(KnowledgeToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(99L, 7L, 10L, 20L, 0, "trace");
        var command = new KnowledgeToolPort.CreateTextCommand(5L, "policy.txt", "Policy content");
        var createdAt = LocalDateTime.of(2026, 10, 1, 20, 0);
        when(port.createText(context.actor(), command)).thenReturn(
                new KnowledgeToolPort.CreateTextResult(42L, 5L, "policy.txt", "READY", 1, createdAt));

        var result = new KnowledgeDocumentCreateTextToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"kbId\":5,\"filename\":\"policy.txt\",\"content\":\"Policy content\"}"));

        assertThat(result.path("documentId").asLong()).isEqualTo(42L);
        assertThat(result.path("status").asText()).isEqualTo("READY");
        verify(port).createText(context.actor(), command);
        verifyNoMoreInteractions(port);
    }
}
