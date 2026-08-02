package com.aiworkmate.service.impl;

import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.dto.KnowledgeSearchItemResponse;
import com.aiworkmate.dto.KnowledgeSearchResponse;
import com.aiworkmate.service.KnowledgeService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PgvectorKnowledgeContextServiceImplTest {

    @Test
    void shouldBuildNumberedTenantOwnedContextFromSearchResults() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        when(knowledgeService.search(eq(7L), any())).thenReturn(new KnowledgeSearchResponse(
                "local", "Qwen3", 1024,
                List.of(new KnowledgeSearchItemResponse(10L, 20L, "handbook.txt",
                        0, "Annual leave policy", 0.91, "DENSE"))));
        PgvectorKnowledgeContextServiceImpl service =
                new PgvectorKnowledgeContextServiceImpl(knowledgeService, new EmbeddingProperties());

        var context = service.retrieve(7L, "How many days?", null);

        assertThat(context.promptContext()).contains("知识来源1", "handbook.txt", "Annual leave policy");
        assertThat(context.references()).hasSize(1);
        assertThat(context.references().get(0).docId()).isEqualTo("10");
    }

    @Test
    void shouldRouteToKnowledgeBaseWhenKbIdProvided() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        when(knowledgeService.searchInKnowledgeBase(eq(7L), eq(3L), any())).thenReturn(new KnowledgeSearchResponse(
                "local", "Qwen3", 1024,
                List.of(new KnowledgeSearchItemResponse(12L, 34L, "spec.txt",
                        1, "Onboarding checklist", 0.88, "HYBRID"))));
        PgvectorKnowledgeContextServiceImpl service =
                new PgvectorKnowledgeContextServiceImpl(knowledgeService, new EmbeddingProperties());

        var context = service.retrieve(7L, "How to onboard?", 3L);

        assertThat(context.promptContext()).contains("知识来源1", "spec.txt", "Onboarding checklist");
        assertThat(context.references()).hasSize(1);
        assertThat(context.references().get(0).docId()).isEqualTo("12");
    }
}
