package com.aiworkmate.service.impl;

import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.dto.KnowledgeSearchItemResponse;
import com.aiworkmate.dto.KnowledgeSearchResponse;
import com.aiworkmate.service.KnowledgeService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class PgvectorKnowledgeContextServiceImplTest {

    @Test
    void ordinaryChatWithoutKnowledgeSearchAccessDoesNotCallRetrieval() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        when(knowledgeService.search(eq(7L), any())).thenThrow(new BusinessException(ErrorCode.PERMISSION_DENIED));
        var service = new PgvectorKnowledgeContextServiceImpl(knowledgeService, new EmbeddingProperties(), access());

        var context = service.retrieve(7L, "Hello", null);

        assertThat(context.hasContext()).isFalse();
        assertThat(context.references()).isEmpty();
        verifyNoInteractions(knowledgeService);
    }

    @Test
    void shouldBuildNumberedTenantOwnedContextFromSearchResults() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        when(knowledgeService.search(eq(7L), any())).thenReturn(new KnowledgeSearchResponse(
                "local", "Qwen3", 1024,
                List.of(new KnowledgeSearchItemResponse(10L, 20L, "handbook.txt",
                        0, "Annual leave policy", 0.91, "DENSE"))));
        PgvectorKnowledgeContextServiceImpl service =
                new PgvectorKnowledgeContextServiceImpl(knowledgeService, new EmbeddingProperties(), access("knowledge:search"));

        var context = service.retrieve(7L, "How many days?", null);

        assertThat(context.promptContext()).contains("知识来源1", "handbook.txt", "Annual leave policy", "内容摘录");
        assertThat(context.promptContext()).contains("[知识来源1：handbook.txt，分块 0，内容摘录：\"Annual leave policy\"]");
        assertThat(context.references()).hasSize(1);
        assertThat(context.references().get(0).docId()).isEqualTo("10");
        assertThat(context.references().get(0).text()).isEqualTo("Annual leave policy");
    }

    @Test
    void shouldTrimReferenceTextByCodePointWithoutBreakingSurrogatePair() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        // 第 300 个 code point 恰好是 emoji（代理对），验证截断不会把它拆开
        String longContent = "a".repeat(299) + "\uD83D\uDE00" + "b".repeat(100);
        when(knowledgeService.search(eq(7L), any())).thenReturn(new KnowledgeSearchResponse(
                "local", "Qwen3", 1024,
                List.of(new KnowledgeSearchItemResponse(10L, 20L, "handbook.txt",
                        0, longContent, 0.91, "DENSE"))));
        PgvectorKnowledgeContextServiceImpl service =
                new PgvectorKnowledgeContextServiceImpl(knowledgeService, new EmbeddingProperties(), access("knowledge:search"));

        var context = service.retrieve(7L, "How many days?", null);

        assertThat(context.references()).hasSize(1);
        String text = context.references().get(0).text();
        assertThat(text.codePointCount(0, text.length())).isEqualTo(300);
        assertThat(text).endsWith("\uD83D\uDE00");
        assertThat(text).doesNotContain("\uFFFD");
    }

    @Test
    void shouldRouteToKnowledgeBaseWhenKbIdProvided() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        when(knowledgeService.searchInKnowledgeBase(eq(7L), eq(3L), any())).thenReturn(new KnowledgeSearchResponse(
                "local", "Qwen3", 1024,
                List.of(new KnowledgeSearchItemResponse(12L, 34L, "spec.txt",
                        1, "Onboarding checklist", 0.88, "HYBRID"))));
        PgvectorKnowledgeContextServiceImpl service =
                new PgvectorKnowledgeContextServiceImpl(knowledgeService, new EmbeddingProperties(), access());

        var context = service.retrieve(7L, "How to onboard?", 3L);

        assertThat(context.promptContext()).contains("知识来源1", "spec.txt", "Onboarding checklist", "内容摘录");
        assertThat(context.references()).hasSize(1);
        assertThat(context.references().get(0).docId()).isEqualTo("12");
    }

    @Test void rechecksLiveSearchPermissionForEveryTurnAndStopsImplicitRetrievalAfterRevocation() {
        var knowledge = mock(KnowledgeService.class);
        var access = mock(UserAccessService.class);
        when(access.resolveActiveUser(7L)).thenReturn(actor("knowledge:search"), actor());
        when(knowledge.search(eq(7L), any())).thenReturn(new KnowledgeSearchResponse("local", "Qwen3", 1024, List.of()));
        var service = new PgvectorKnowledgeContextServiceImpl(knowledge, new EmbeddingProperties(), access);

        assertThat(service.retrieve(7L, "First", null).hasContext()).isFalse();
        assertThat(service.retrieve(7L, "Next", null).hasContext()).isFalse();

        verify(access, times(2)).resolveActiveUser(7L);
        verify(knowledge, times(1)).search(eq(7L), any());
    }

    @Test void explicitKnowledgeBaseDenialsDoNotFallBackToSuccessfulPlainChat() {
        var knowledge = mock(KnowledgeService.class);
        when(knowledge.searchInKnowledgeBase(eq(7L), eq(99L), any()))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        var service = new PgvectorKnowledgeContextServiceImpl(knowledge, new EmbeddingProperties(), access());

        assertThatThrownBy(() -> service.retrieve(7L, "Question", 99L))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getErrorCode()));
    }

    @Test void authorizedRetrievalErrorsAreNotSuppressed() {
        var knowledge = mock(KnowledgeService.class);
        when(knowledge.search(eq(7L), any())).thenThrow(new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID));
        var service = new PgvectorKnowledgeContextServiceImpl(knowledge, new EmbeddingProperties(), access("knowledge:search"));

        assertThatThrownBy(() -> service.retrieve(7L, "Question", null))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.EMBEDDING_RESPONSE_INVALID.getErrorCode()));
    }

    @Test void missingInactiveOrUnreadableIdentityNeverProducesSuccessfulContext() {
        var knowledge = mock(KnowledgeService.class);
        var access = mock(UserAccessService.class);
        var service = new PgvectorKnowledgeContextServiceImpl(knowledge, new EmbeddingProperties(), access);
        assertThatThrownBy(() -> service.retrieve(null, "Question", null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(access);
        assertThatThrownBy(() -> service.retrieve(7L, "Question", null)).isInstanceOf(BusinessException.class);
        when(access.resolveActiveUser(7L)).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> service.retrieve(7L, "Question", null)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(knowledge);
    }

    private static ResolvedUserAccess actor(String... permissions) {
        return new ResolvedUserAccess(7L, "reader", "EMPLOYEE", List.of(permissions));
    }

    private static UserAccessService access(String... permissions) {
        var access = mock(UserAccessService.class);
        when(access.resolveActiveUser(7L)).thenReturn(actor(permissions));
        return access;
    }
}
