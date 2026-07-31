package com.aiworkmate.service.impl;

import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.dto.KnowledgeSearchRequest;
import com.aiworkmate.mapper.KnowledgeDocumentMapper;
import com.aiworkmate.service.EmbeddingService;
import com.aiworkmate.service.KnowledgeChunker;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.EmbeddingDescriptor;
import com.aiworkmate.service.model.EmbeddingResult;
import com.aiworkmate.service.model.KnowledgeSearchRow;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeServiceImplTest {

    @Test
    void searchShouldAlwaysScopeByResolvedTenantAndUserAndCurrentModel() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        UserAccessService accessService = mock(UserAccessService.class);
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(3);
        when(accessService.resolveActiveUser(7L)).thenReturn(new ResolvedUserAccess(
                7L, "alice", 99L, "EMPLOYEE", List.of("EMPLOYEE"),
                List.of(), List.of("SELF"), 1L));
        when(embeddingService.current()).thenReturn(new EmbeddingDescriptor("api", "model-a", 3));
        when(embeddingService.embed(List.of("policy")))
                .thenReturn(new EmbeddingResult("api", "model-a",
                        List.of(new float[]{0.1F, 0.2F, 0.3F})));
        when(mapper.selectCount(any())).thenReturn(1L);
        KnowledgeSearchRow row = new KnowledgeSearchRow();
        row.setDocId(1L);
        row.setChunkId(2L);
        row.setFilename("policy.txt");
        row.setChunkIndex(0);
        row.setContent("policy content");
        row.setScore(0.9);
        when(mapper.search(eq(99L), eq(7L), anyString(), eq("api"), eq("model-a"),
                eq(0.4), eq(3))).thenReturn(List.of(row));
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(mapper, embeddingService,
                new KnowledgeChunker(properties), accessService, properties, new ObjectMapper());

        var result = service.search(7L, new KnowledgeSearchRequest("policy", 3, 0.4));

        assertThat(result.records()).hasSize(1);
        assertThat(result.provider()).isEqualTo("api");
        verify(mapper).search(eq(99L), eq(7L), anyString(), eq("api"), eq("model-a"),
                eq(0.4), eq(3));
    }
}
