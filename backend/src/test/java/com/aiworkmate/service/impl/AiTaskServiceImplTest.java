package com.aiworkmate.service.impl;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.planner.AgentPlanner;
import com.aiworkmate.agent.planner.PageContextFilter;
import com.aiworkmate.agent.planner.PlannerCandidate;
import com.aiworkmate.agent.registry.*;
import com.aiworkmate.agent.task.*;
import com.aiworkmate.config.AiRuntimeProperties;
import com.aiworkmate.dto.AiTaskExecuteRequest;
import com.aiworkmate.dto.AiTaskPlanRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiTaskServiceImplTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentRuntimeProperties runtime = new AgentRuntimeProperties();
    private final AiRuntimeProperties aiRuntime = new AiRuntimeProperties();
    private final ToolRegistry registry = mock(ToolRegistry.class);
    private final AgentPlanner planner = mock(AgentPlanner.class);
    private final AgentTaskMapper taskMapper = mock(AgentTaskMapper.class);
    private final AgentTaskStepMapper stepMapper = mock(AgentTaskStepMapper.class);
    private final AgentIdempotencyService idempotency = mock(AgentIdempotencyService.class);
    private final AgentTaskEventService events = mock(AgentTaskEventService.class);
    private final AgentTaskApiService taskApiService = mock(AgentTaskApiService.class);
    private final AgentApiRateLimiter rateLimiter = mock(AgentApiRateLimiter.class);
    private final org.springframework.context.ApplicationEventPublisher eventPublisher =
            mock(org.springframework.context.ApplicationEventPublisher.class);
    private final AgentHashing hashing = new AgentHashing(mapper);
    private AiTaskServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        runtime.setEnabled(true);
        runtime.setPlanningEnabled(true);
        runtime.setExecutionEnabled(true);
        aiRuntime.setApiKey("unit-test-key");
        service = new AiTaskServiceImpl(runtime, aiRuntime, registry, planner,
                new PageContextFilter(mapper, runtime), taskMapper, stepMapper, idempotency, events,
                taskApiService, rateLimiter, hashing, mapper, eventPublisher);
        ToolDefinition tool = tool();
        when(registry.resolveAllowedTools(any(), eq("todo-list"))).thenReturn(List.of(tool));
        when(planner.plan(anyString(), eq("todo-list"), any(), anyList())).thenReturn(new PlannerCandidate(
                "查询本人待办", List.of(new PlannerCandidate.Step("todo.query",
                mapper.readTree("{\"page\":1,\"size\":20}")))));
        doAnswer(invocation -> {
            AgentTask task = invocation.getArgument(0);
            task.setId(88L);
            return 1;
        }).when(taskMapper).insertReceived(any(AgentTask.class));
        when(idempotency.bind(anyLong(), anyLong(), eq(IdempotencyOperation.PLAN), anyString(), anyString(), eq(88L)))
                .thenReturn(new IdempotencyBinding(true, 88L));
        when(taskMapper.transition(88L, "RECEIVED", "PLANNING", 0L)).thenReturn(1);
        when(taskMapper.finalizePlan(eq(88L), eq(1L), anyString(), anyString(), eq("L0"), eq("PLAN_READY"),
                anyString(), anyString(), anyLong(), eq(1))).thenReturn(1);
        when(stepMapper.insertPending(any(AgentTaskStep.class))).thenReturn(1);
    }

    @Test
    void persistsServerBoundPlanAndFilteredContext() {
        AiTaskPlanRequest request = new AiTaskPlanRequest();
        request.setInput("查看我的待办");
        request.setPageId("todo-list");
        request.setPageContext(mapper.createObjectNode().put("status", "OPEN").put("userId", 999));

        var response = service.plan(request, "plan-key-123", user());

        assertThat(response.status()).isEqualTo("PLAN_READY");
        assertThat(response.planHash()).startsWith("sha256:");
        ArgumentCaptor<AgentTask> task = ArgumentCaptor.forClass(AgentTask.class);
        verify(taskMapper).insertReceived(task.capture());
        assertThat(task.getValue().getTaskNo()).hasSize(36);
        assertThat(task.getValue().getPageContext()).isEqualTo("{\"status\":\"OPEN\"}");
        ArgumentCaptor<AgentTaskStep> step = ArgumentCaptor.forClass(AgentTaskStep.class);
        verify(stepMapper).insertPending(step.capture());
        assertThat(step.getValue().getToolCode()).isEqualTo("todo.query");
        assertThat(step.getValue().getArgs()).doesNotContain("userId");
        verify(events).publish(eq(88L), eq("snapshot"), any(), anyString());
    }

    @Test
    void queuesL0TaskWithIndependentIdempotencyBinding() {
        AgentTask task = new AgentTask();
        task.setId(88L); task.setTaskNo("00000000-0000-4000-8000-000000000001");
        task.setTenantId(1L); task.setUserId(7L); task.setPlanVersion(1);
        task.setPlanHash("sha256:" + "a".repeat(64)); task.setMaxRiskLevel("L0");
        task.setStatus("PLAN_READY"); task.setTraceId("trace");
        when(taskMapper.selectOwned(1L, 7L, task.getTaskNo())).thenReturn(task);
        when(idempotency.bind(eq(1L), eq(7L), eq(IdempotencyOperation.EXECUTE), eq("execute-key-123"),
                anyString(), eq(88L))).thenReturn(new IdempotencyBinding(true, 88L));
        when(taskMapper.queuePlanReady(eq(88L), eq(1L), eq(7L), eq(1), eq(task.getPlanHash()), any()))
                .thenReturn(1);

        var response = service.execute(task.getTaskNo(), new AiTaskExecuteRequest(1, task.getPlanHash(), null),
                "execute-key-123", user());
        assertThat(response.status()).isEqualTo("QUEUED");
        verify(taskMapper).queuePlanReady(eq(88L), eq(1L), eq(7L), eq(1), eq(task.getPlanHash()), any());
        verify(eventPublisher).publishEvent(any(AgentTaskQueuedEvent.class));
    }

    @Test
    void consumesConfirmationBeforeQueueingWriteTask() {
        AgentTask task = new AgentTask();
        task.setId(88L); task.setTaskNo("00000000-0000-4000-8000-000000000002");
        task.setTenantId(1L); task.setUserId(7L); task.setPlanVersion(1);
        task.setPlanHash("sha256:" + "b".repeat(64)); task.setMaxRiskLevel("L1");
        task.setStatus("WAITING_CONFIRMATION"); task.setTraceId("trace");
        when(taskMapper.selectOwned(1L, 7L, task.getTaskNo())).thenReturn(task);
        when(idempotency.bind(eq(1L), eq(7L), eq(IdempotencyOperation.EXECUTE), eq("execute-write-key"),
                anyString(), eq(88L))).thenReturn(new IdempotencyBinding(true, 88L));

        var response = service.execute(task.getTaskNo(),
                new AiTaskExecuteRequest(1, task.getPlanHash(), "confirmation-token"),
                "execute-write-key", user());

        assertThat(response.status()).isEqualTo("QUEUED");
        verify(taskApiService).consumeConfirmation(
                user(), task.getTaskNo(), 1, task.getPlanHash(), "confirmation-token");
        verify(taskMapper, never()).queuePlanReady(anyLong(), anyLong(), anyLong(), anyInt(), anyString(), any());
        verify(eventPublisher).publishEvent(any(AgentTaskQueuedEvent.class));
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(7L, "alice", 1L, "EMPLOYEE", List.of("EMPLOYEE"),
                List.of("todo:read"), List.of("SELF"), 1L);
    }

    private ToolDefinition tool() throws Exception {
        return ToolDefinition.create("todo.query", "Todo query", "Query todos", "Query current user's todos", "1.0.0",
                mapper.readTree("{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"page\":{\"type\":\"integer\"},\"size\":{\"type\":\"integer\"}},\"required\":[\"page\",\"size\"]}"),
                mapper.readTree("{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}"),
                RiskLevel.L0, Set.of("todo:read"), PermissionMode.ALL, OwnershipPolicy.SELF,
                RetryPolicy.READ_ONLY_SAFE, SideEffect.NONE, ConfirmationPolicy.NONE, 50, 262144, 15000, "FULL");
    }
}
