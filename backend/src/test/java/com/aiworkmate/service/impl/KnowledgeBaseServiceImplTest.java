package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.dto.KnowledgeBaseCreateRequest;
import com.aiworkmate.dto.KnowledgeBaseUpdateRequest;
import com.aiworkmate.entity.KnowledgeBase;
import com.aiworkmate.mapper.KnowledgeBaseMapper;
import com.aiworkmate.service.EmbeddingService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.EmbeddingDescriptor;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgeBaseServiceImplTest {
    private final KnowledgeBaseMapper mapper = mock(KnowledgeBaseMapper.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final UserAccessService accessService = mock(UserAccessService.class);
    private final KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(
            mapper, embeddingService, new EmbeddingProperties(), accessService);

    @Test
    void agentQueryReturnsOnlyOwnedBasesAndBoundsTheRequestedLimit() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("knowledge:search")));
        when(mapper.selectOwned(99L, 7L, 50)).thenReturn(List.of(ownedBase()));

        var result = service.queryAgent(7L, null, 500);

        assertThat(result).extracting(response -> response.id()).containsExactly(42L);
        verify(mapper).selectOwned(99L, 7L, 50);
    }

    @Test
    void agentQueryFailsClosedBeforePersistenceWhenPermissionWasRevoked() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of()));

        assertThatThrownBy(() -> service.queryAgent(7L, null, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.PERMISSION_DENIED.getErrorCode()));

        verifyNoInteractions(mapper, embeddingService);
    }

    @Test
    void agentCreationUsesTrustedOwnerAndCurrentEmbeddingConfiguration() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("knowledge:search")));
        when(embeddingService.current()).thenReturn(new EmbeddingDescriptor("local", "embedding-v1", 1024));
        when(mapper.insert(any(KnowledgeBase.class))).thenAnswer(invocation -> {
            var knowledgeBase = invocation.<KnowledgeBase>getArgument(0);
            knowledgeBase.setId(42L);
            return 1;
        });

        var result = service.createAgent(7L,
                new KnowledgeBaseCreateRequest(" 研发制度 ", null, " 团队制度资料 "));

        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.name()).isEqualTo("研发制度");
        assertThat(result.icon()).isEqualTo("knowledge-base");
        assertThat(result.embeddingProvider()).isEqualTo("local");
        verify(mapper).insert(argThat((KnowledgeBase base) -> base.getTenantId().equals(99L)
                && base.getUserId().equals(7L)));
    }

    @Test
    void agentCreationFailsClosedBeforeEmbeddingOrPersistenceWhenPermissionWasRevoked() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of()));

        assertThatThrownBy(() -> service.createAgent(7L,
                new KnowledgeBaseCreateRequest("研发制度", null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.PERMISSION_DENIED.getErrorCode()));

        verifyNoInteractions(embeddingService, mapper);
    }

    @Test
    void agentUpdateRequiresPermissionAndOwnedKnowledgeBase() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("knowledge:search")));
        when(mapper.selectOne(any())).thenReturn(ownedBase());

        var result = service.updateAgent(7L, 42L,
                new KnowledgeBaseUpdateRequest("研发规范", null, null, 1200, 100, 8, 4));

        assertThat(result.name()).isEqualTo("研发规范");
        assertThat(result.chunkSize()).isEqualTo(1200);
        verify(mapper).updateById(argThat((KnowledgeBase base) -> base.getTenantId().equals(99L)
                && base.getUserId().equals(7L) && base.getId().equals(42L)));
    }

    @Test
    void agentUpdateFailsClosedBeforeOwnershipLookupWhenPermissionWasRevoked() {
        when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of()));

        assertThatThrownBy(() -> service.updateAgent(7L, 42L,
                new KnowledgeBaseUpdateRequest("研发规范", null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.PERMISSION_DENIED.getErrorCode()));

        verifyNoInteractions(mapper, embeddingService);
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(7L, "owner", 99L, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), permissions, List.of("SELF"), 1L);
    }

    private KnowledgeBase ownedBase() {
        KnowledgeBase base = new KnowledgeBase();
        base.setId(42L);
        base.setTenantId(99L);
        base.setUserId(7L);
        base.setName("研发制度");
        base.setIcon("book");
        base.setDescription("团队制度资料");
        base.setEmbeddingProvider("local");
        base.setEmbeddingModel("embedding-v1");
        base.setChunkSize(1000);
        base.setChunkOverlap(120);
        base.setDenseTopK(5);
        base.setSparseTopK(5);
        base.setCreatedAt(LocalDateTime.of(2026, 10, 1, 20, 50));
        base.setUpdatedAt(base.getCreatedAt());
        return base;
    }
}
