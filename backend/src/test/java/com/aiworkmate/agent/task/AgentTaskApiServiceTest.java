package com.aiworkmate.agent.task;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.AgentConfirmationTokenRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTaskApiServiceTest {
    private static final String TASK_NO = "00000000-0000-4000-8000-000000000001";
    private static final String PLAN_HASH = "sha256:" + "a".repeat(64);

    private final AgentTaskMapper taskMapper = mock(AgentTaskMapper.class);
    private final AgentTaskStepMapper stepMapper = mock(AgentTaskStepMapper.class);
    private final AgentTaskEventService eventService = mock(AgentTaskEventService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentRuntimeProperties properties = new AgentRuntimeProperties();
    private final AuthenticatedUser user = new AuthenticatedUser(
            7L, "alice", 9L, "EMPLOYEE", List.of("EMPLOYEE"), List.of(), List.of("SELF"), 1L);
    private AgentTaskApiService service;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setExecutionEnabled(true);
        service = new AgentTaskApiService(taskMapper, stepMapper, new AgentHashing(objectMapper),
                properties, eventService, objectMapper);
    }

    @Test
    void signsOpaqueCredentialAndPersistsOnlyItsHash() {
        AgentTask task = waitingTask();
        when(taskMapper.selectOwned(9L, 7L, TASK_NO)).thenReturn(task);
        when(taskMapper.issueConfirmation(anyLong(), anyLong(), anyLong(), anyLong(), anyInt(),
                anyString(), anyString(), any(LocalDateTime.class))).thenReturn(1);

        var response = service.issueConfirmation(user, TASK_NO,
                new AgentConfirmationTokenRequest(1, PLAN_HASH));

        assertThat(response.confirmationToken()).hasSizeGreaterThanOrEqualTo(40).doesNotContain("sha256:");
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(taskMapper).issueConfirmation(eq(11L), eq(9L), eq(7L), eq(3L), eq(1), eq(PLAN_HASH),
                hash.capture(), any(LocalDateTime.class));
        assertThat(hash.getValue()).startsWith("sha256:").isNotEqualTo(response.confirmationToken());
    }

    @Test
    void rejectsStalePlanBeforeSigning() {
        when(taskMapper.selectOwned(9L, 7L, TASK_NO)).thenReturn(waitingTask());

        assertThatThrownBy(() -> service.issueConfirmation(user, TASK_NO,
                new AgentConfirmationTokenRequest(2, "sha256:" + "b".repeat(64))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GATEWAY_STALE.getErrorCode()));
        verify(taskMapper, never()).issueConfirmation(anyLong(), anyLong(), anyLong(), anyLong(), anyInt(),
                anyString(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void consumesConfirmationAtomicallyAndRejectsReplay() {
        when(taskMapper.consumeConfirmation(eq(9L), eq(7L), eq(TASK_NO), eq(1), eq(PLAN_HASH),
                anyString(), any())).thenReturn(1, 0);

        service.consumeConfirmation(user, TASK_NO, 1, PLAN_HASH, "opaque-token");

        assertThatThrownBy(() -> service.consumeConfirmation(user, TASK_NO, 1, PLAN_HASH, "opaque-token"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CONFIRMATION_EXPIRED.getErrorCode()));
    }

    @Test
    void cancellationUsesOwnerStateAndVersionConditions() {
        AgentTask task = waitingTask();
        when(taskMapper.selectOwned(9L, 7L, TASK_NO)).thenReturn(task, cancelled(task));
        when(taskMapper.cancelOwned(11L, 9L, 7L, "WAITING_CONFIRMATION", 3L)).thenReturn(1);
        when(stepMapper.selectByTaskId(11L)).thenReturn(List.of());

        var response = service.cancel(user, TASK_NO);

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(stepMapper).cancelPending(11L);
        verify(eventService).publish(eq(11L), eq("snapshot"), any(), eq("trace-task-api"));
    }

    @Test
    void listAlwaysScopesQueriesAndClampsPageSize() {
        when(taskMapper.selectOwnedPage(9L, 7L, null, null, null, 50, 0)).thenReturn(List.of());
        when(taskMapper.countOwned(9L, 7L, null, null, null)).thenReturn(0L);

        var response = service.list(user, null, null, null, 0, 500);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(50);
        verify(taskMapper).selectOwnedPage(9L, 7L, null, null, null, 50, 0);
    }

    @Test
    void rateLimitsConfirmationPerTenantUserAndTask() {
        when(taskMapper.selectOwned(9L, 7L, TASK_NO)).thenReturn(waitingTask());
        when(taskMapper.issueConfirmation(anyLong(), anyLong(), anyLong(), anyLong(), anyInt(),
                anyString(), anyString(), any(LocalDateTime.class))).thenReturn(1);
        AgentConfirmationTokenRequest request = new AgentConfirmationTokenRequest(1, PLAN_HASH);

        service.issueConfirmation(user, TASK_NO, request);
        service.issueConfirmation(user, TASK_NO, request);
        service.issueConfirmation(user, TASK_NO, request);

        assertThatThrownBy(() -> service.issueConfirmation(user, TASK_NO, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED.getErrorCode()));
    }

    private AgentTask waitingTask() {
        AgentTask task = new AgentTask();
        task.setId(11L);
        task.setTaskNo(TASK_NO);
        task.setTenantId(9L);
        task.setUserId(7L);
        task.setPageId("my-applications");
        task.setInput("create draft");
        task.setPageContext("{}");
        task.setPlan("{}");
        task.setPlanHash(PLAN_HASH);
        task.setPlanVersion(1);
        task.setMaxRiskLevel("L1");
        task.setStatus("WAITING_CONFIRMATION");
        task.setVersion(3L);
        task.setTraceId("trace-task-api");
        return task;
    }

    private AgentTask cancelled(AgentTask source) {
        AgentTask task = waitingTask();
        task.setStatus("CANCELLED");
        task.setVersion(source.getVersion() + 1);
        task.setFinishedAt(LocalDateTime.now());
        return task;
    }
}
