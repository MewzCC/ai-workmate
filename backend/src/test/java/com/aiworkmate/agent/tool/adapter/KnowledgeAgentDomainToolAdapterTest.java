package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.KnowledgeBaseCreateRequest;
import com.aiworkmate.dto.KnowledgeBaseResponse;
import com.aiworkmate.dto.KnowledgeBaseUpdateRequest;
import com.aiworkmate.dto.KnowledgeDocumentCreateRequest;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.KnowledgeDocumentResponse;
import com.aiworkmate.service.KnowledgeBaseService;
import com.aiworkmate.service.KnowledgeService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class KnowledgeAgentDomainToolAdapterTest {
    private final KnowledgeService service = mock(KnowledgeService.class);
    private final KnowledgeBaseService baseService = mock(KnowledgeBaseService.class);
    private final KnowledgeAgentDomainToolAdapter adapter = new KnowledgeAgentDomainToolAdapter(service, baseService);
    private final ToolActorContext actor = new ToolActorContext(99, 7, 10, 20, 0, "trace");

    @Test
    void delegatesOwnedBaseQueryAndRemovesEmbeddingInternals() {
        var query = new KnowledgeToolPort.BaseQuery(null, 20);
        var createdAt = LocalDateTime.of(2026, 10, 1, 20, 30);
        var updatedAt = createdAt.plusMinutes(1);
        when(baseService.queryAgent(7L, null, 20)).thenReturn(List.of(
                new KnowledgeBaseResponse(42L, "研发制度", "book", null, 2, 8,
                        "internal-provider", "internal-model", "internal-reranker",
                        1000, 120, 5, 5, createdAt, updatedAt)));

        assertThat(adapter.queryBases(actor, query)).isEqualTo(new KnowledgeToolPort.BaseQueryResult(List.of(
                new KnowledgeToolPort.BaseItem(42L, "研发制度", "book", null, 2, 8, createdAt, updatedAt))));
        verify(baseService).queryAgent(7L, null, 20);
        verifyNoMoreInteractions(service, baseService);
    }

    @Test
    void delegatesOwnedDocumentQueryAndRemovesEmbeddingInternals() {
        var query = new KnowledgeToolPort.DocumentQuery(5L, null, 1, 20);
        var createdAt = LocalDateTime.of(2026, 10, 1, 20, 30);
        var updatedAt = createdAt.plusMinutes(1);
        when(service.queryDocumentsAgent(7L, 5L, null, 1, 20)).thenReturn(PageResponse.of(List.of(
                new KnowledgeDocumentResponse(42L, "policy.txt", 128, "TEXT", 3, "READY",
                        "internal-provider", "internal-model", createdAt, updatedAt)), 1, 1, 20));

        assertThat(adapter.queryDocuments(actor, query)).isEqualTo(new KnowledgeToolPort.DocumentQueryResult(
                5L, List.of(new KnowledgeToolPort.DocumentItem(42L, "policy.txt", 128, "TEXT", 3,
                        "READY", createdAt, updatedAt)), 1, 1, 20));
        verify(service).queryDocumentsAgent(7L, 5L, null, 1, 20);
        verifyNoMoreInteractions(service, baseService);
    }

    @Test
    void delegatesBaseCreationToAgentSpecificDomainEntry() {
        var command = new KnowledgeToolPort.CreateBaseCommand("研发制度", "book", "团队制度资料");
        var request = new KnowledgeBaseCreateRequest("研发制度", "book", "团队制度资料");
        var createdAt = LocalDateTime.of(2026, 10, 1, 20, 30);
        var response = new KnowledgeBaseResponse(42L, "研发制度", "book", "团队制度资料",
                0, 0, "local", "model", null, 1000, 120, 5, 5, createdAt, createdAt);
        when(baseService.createAgent(7L, request)).thenReturn(response);

        assertThat(adapter.createBase(actor, command)).isEqualTo(
                new KnowledgeToolPort.CreateBaseResult(42L, "研发制度", "book", "团队制度资料", 0, 0, createdAt));
        verify(baseService).createAgent(7L, request);
        verifyNoMoreInteractions(service, baseService);
    }

    @Test
    void delegatesBaseUpdateAndRemovesEmbeddingInternals() {
        var command = new KnowledgeToolPort.UpdateBaseCommand(
                42, "研发规范", "policy", "更新说明", 1200, 100, 8, 4);
        var request = new KnowledgeBaseUpdateRequest(
                "研发规范", "policy", "更新说明", 1200, 100, 8, 4);
        var updatedAt = LocalDateTime.of(2026, 10, 1, 21, 0);
        var response = new KnowledgeBaseResponse(42L, "研发规范", "policy", "更新说明",
                2, 8, "internal-provider", "internal-model", "internal-reranker",
                1200, 100, 8, 4, updatedAt.minusHours(1), updatedAt);
        when(baseService.updateAgent(7L, 42L, request)).thenReturn(response);

        assertThat(adapter.updateBase(actor, command)).isEqualTo(
                new KnowledgeToolPort.UpdateBaseResult(42L, "研发规范", "policy", "更新说明",
                        2, 8, 1200, 100, 8, 4, updatedAt));
        verify(baseService).updateAgent(7L, 42L, request);
        verifyNoMoreInteractions(service, baseService);
    }

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
