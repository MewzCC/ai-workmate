package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.KnowledgeDocumentCreateRequest;
import com.aiworkmate.dto.KnowledgeDocumentResponse;
import com.aiworkmate.service.KnowledgeService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class KnowledgeAgentDomainToolAdapterTest {
    private final KnowledgeService service = mock(KnowledgeService.class);
    private final KnowledgeAgentDomainToolAdapter adapter = new KnowledgeAgentDomainToolAdapter(service);
    private final ToolActorContext actor = new ToolActorContext(99, 7, 10, 20, 0, "trace");

    @Test
    void delegatesTextCreationToAgentSpecificDomainEntry() {
        var command = new KnowledgeToolPort.CreateTextCommand(5, "policy.txt", "Policy content");
        var request = new KnowledgeDocumentCreateRequest(5L, "policy.txt", "Policy content");
        var createdAt = LocalDateTime.of(2026, 10, 1, 20, 0);
        var response = new KnowledgeDocumentResponse(42L, "policy.txt", 14L, "TEXT", 1,
                "READY", "local", "model", createdAt, createdAt);
        when(service.createAgent(7L, request)).thenReturn(response);

        assertThat(adapter.createText(actor, command)).isEqualTo(
                new KnowledgeToolPort.CreateTextResult(42L, 5L, "policy.txt", "READY", 1, createdAt));
        verify(service).createAgent(7L, request);
        verifyNoMoreInteractions(service);
    }
}
