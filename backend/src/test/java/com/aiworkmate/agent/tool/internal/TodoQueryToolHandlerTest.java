package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.TodoResponse;
import com.aiworkmate.service.LeaveWorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoQueryToolHandlerTest {
    @Mock
    private LeaveWorkflowService leaveWorkflowService;

    private ObjectMapper objectMapper;
    private TodoQueryToolHandler handler;
    private TrustedToolContext context;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new TodoQueryToolHandler(leaveWorkflowService, objectMapper);
        context = new TrustedToolContext(91L, 7L, 10L, 20L, 1, "trace");
    }

    @Test
    void returnsOnlyBoundedSafeFieldsForTrustedUser() throws Exception {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 25, 9, 30);
        LocalDateTime dueAt = LocalDateTime.of(2026, 8, 26, 9, 30);
        TodoResponse todo = new TodoResponse(
                31L, 41L, 999L, "张三", "ANNUAL", 2, "PENDING", 3,
                submittedAt, dueAt, false, "private-avatar-key", submittedAt,
                "/api/users/999/avatar?token=secret");
        when(leaveWorkflowService.todos(7L, "PENDING", null, null, 1, 20))
                .thenReturn(PageResponse.of(List.of(todo), 1, 1, 20));

        var result = handler.execute(context, objectMapper.readTree("{\"status\":\"PENDING\"}"));

        assertThat(result.path("items")).hasSize(1);
        assertThat(result.at("/items/0/applicantName").asText()).isEqualTo("张三");
        assertThat(result.at("/items/0/submittedAt").asText()).isEqualTo("2026-08-25T09:30");
        assertThat(result.at("/items/0/applicantUserId").isMissingNode()).isTrue();
        assertThat(result.at("/items/0/applicantAvatar").isMissingNode()).isTrue();
        assertThat(result.at("/items/0/applicantAvatarUrl").isMissingNode()).isTrue();
        assertThat(result.toString()).doesNotContain("secret", "private-avatar-key");
        verify(leaveWorkflowService).todos(7L, "PENDING", null, null, 1, 20);
    }

    @Test
    void returnsEmptyResultWithoutFabricatingData() throws Exception {
        when(leaveWorkflowService.todos(7L, null, null, null, 1, 20))
                .thenReturn(PageResponse.of(List.of(), 0, 1, 20));

        var result = handler.execute(context, objectMapper.readTree("{}"));

        assertThat(result.path("items")).isEmpty();
        assertThat(result.path("total").asLong()).isZero();
    }

    @Test
    void neverUsesCallerSuppliedIdentityAndCapsSizeDefensively() throws Exception {
        when(leaveWorkflowService.todos(7L, null, null, null, 2, 50))
                .thenReturn(PageResponse.of(List.of(), 0, 2, 50));

        handler.execute(context, objectMapper.readTree("{\"page\":2,\"size\":500}"));

        verify(leaveWorkflowService).todos(7L, null, null, null, 2, 50);
    }

    @Test
    void rejectsMalformedOrReversedTimeRangeBeforeDomainCall() throws Exception {
        assertThatThrownBy(() -> handler.execute(
                context, objectMapper.readTree("{\"from\":\"not-a-date\"}")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> handler.execute(
                context, objectMapper.readTree(
                        "{\"from\":\"2026-08-26T00:00:00\",\"to\":\"2026-08-25T00:00:00\"}")))
                .isInstanceOf(BusinessException.class);

        verify(leaveWorkflowService, never()).todos(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }
}
