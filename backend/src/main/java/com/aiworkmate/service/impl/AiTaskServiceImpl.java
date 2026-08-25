package com.aiworkmate.service.impl;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.planner.AgentPlanner;
import com.aiworkmate.agent.planner.PageContextFilter;
import com.aiworkmate.agent.planner.PlannerCandidate;
import com.aiworkmate.agent.planner.StructuredAgentPlanner;
import com.aiworkmate.agent.registry.RiskLevel;
import com.aiworkmate.agent.registry.ToolDefinition;
import com.aiworkmate.agent.registry.ToolRegistry;
import com.aiworkmate.agent.task.*;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.TraceContext;
import com.aiworkmate.config.AiRuntimeProperties;
import com.aiworkmate.dto.*;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.AiTaskService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiTaskServiceImpl implements AiTaskService {
    private final AgentRuntimeProperties runtime;
    private final AiRuntimeProperties aiRuntime;
    private final ToolRegistry registry;
    private final AgentPlanner planner;
    private final PageContextFilter contextFilter;
    private final AgentTaskMapper taskMapper;
    private final AgentTaskStepMapper stepMapper;
    private final AgentIdempotencyService idempotencyService;
    private final AgentTaskEventService eventService;
    private final AgentApiRateLimiter rateLimiter;
    private final AgentHashing hashing;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional
    public AiTaskPlanResponse plan(AiTaskPlanRequest request, String idempotencyKey, AuthenticatedUser user) {
        requirePlanningAvailable();
        rateLimiter.checkPlan(user.tenantId(), user.userId());
        if (request.getInput().getBytes(StandardCharsets.UTF_8).length > runtime.getLimits().getInputMaxBytes())
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        JsonNode safeContext = contextFilter.filter(request.getPageId(), request.getPageContext());
        List<ToolDefinition> allowed = registry.resolveAllowedTools(access(user), request.getPageId());
        if (allowed.isEmpty()) throw new BusinessException(ErrorCode.AI_TASK_CAPABILITY_UNAVAILABLE);

        ObjectNode requestNode = objectMapper.createObjectNode().put("input", request.getInput())
                .put("pageId", request.getPageId());
        requestNode.set("pageContext", safeContext);
        AgentTask task = newTask(request, safeContext, user);
        if (taskMapper.insertReceived(task) != 1 || task.getId() == null)
            throw new IllegalStateException("Agent task persistence failed");
        IdempotencyBinding binding = idempotencyService.bind(user.tenantId(), user.userId(), IdempotencyOperation.PLAN,
                idempotencyKey, hashing.hash(requestNode), task.getId());
        if (!binding.created()) {
            taskMapper.deleteById(task.getId());
            AgentTask existing = taskMapper.selectById(binding.taskId());
            if (existing == null || !user.tenantId().equals(existing.getTenantId())
                    || !user.userId().equals(existing.getUserId())) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            return response(existing, stepMapper.selectByTaskId(existing.getId()));
        }
        if (taskMapper.transition(task.getId(), "RECEIVED", "PLANNING", 0L) != 1)
            throw new BusinessException(ErrorCode.INVALID_TASK_STATE);

        long started = System.nanoTime();
        PlannerCandidate candidate = planner.plan(request.getInput(), request.getPageId(), safeContext, allowed);
        Map<String, ToolDefinition> definitions = allowed.stream()
                .collect(Collectors.toUnmodifiableMap(ToolDefinition::code, Function.identity()));
        BuiltPlan built = buildPlan(task.getId(), task.getTraceId(), candidate, definitions);
        String targetStatus = "L0".equals(built.riskLevel()) ? "PLAN_READY" : "WAITING_CONFIRMATION";
        if (taskMapper.finalizePlan(task.getId(), 1L, json(built.plan()), built.planHash(), built.riskLevel(),
                targetStatus, "structured-chat-client", StructuredAgentPlanner.PROMPT_VERSION,
                (System.nanoTime() - started) / 1_000_000L, built.steps().size()) != 1)
            throw new BusinessException(ErrorCode.INVALID_TASK_STATE);
        built.steps().forEach(step -> {
            if (stepMapper.insertPending(step) != 1) throw new IllegalStateException("Agent step persistence failed");
        });
        eventService.publish(task.getId(), "snapshot", objectMapper.createObjectNode()
                .put("taskId", task.getTaskNo()).put("status", targetStatus)
                .put("planVersion", 1).put("planHash", built.planHash()), task.getTraceId());
        task.setStatus(targetStatus);
        task.setPlan(json(built.plan()));
        task.setPlanHash(built.planHash());
        task.setMaxRiskLevel(built.riskLevel());
        return response(task, built.steps());
    }

    @Override
    @Transactional
    public AiTaskExecuteResponse execute(String taskNo, AiTaskExecuteRequest request,
                                         String idempotencyKey, AuthenticatedUser user) {
        if (!runtime.isEnabled() || !runtime.isExecutionEnabled())
            throw new BusinessException(ErrorCode.AI_TASK_CAPABILITY_UNAVAILABLE);
        rateLimiter.checkExecute(user.tenantId(), user.userId());
        AgentTask task = taskMapper.selectOwned(user.tenantId(), user.userId(), taskNo);
        if (task == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!request.planVersion().equals(task.getPlanVersion()) || !request.planHash().equals(task.getPlanHash()))
            throw new BusinessException(ErrorCode.GATEWAY_STALE);
        ObjectNode hashInput = objectMapper.createObjectNode().put("taskId", taskNo)
                .put("planVersion", request.planVersion()).put("planHash", request.planHash());
        if (StringUtils.hasText(request.confirmationToken()))
            hashInput.put("confirmationTokenHash", hashing.sha256(request.confirmationToken()));
        IdempotencyBinding binding = idempotencyService.bind(user.tenantId(), user.userId(), IdempotencyOperation.EXECUTE,
                idempotencyKey, hashing.hash(hashInput), task.getId());
        if (!binding.created()) return executionResponse(taskNo, task.getStatus());

        if (!"L0".equals(task.getMaxRiskLevel())) throw new BusinessException(ErrorCode.CONFIRMATION_REQUIRED);
        if (StringUtils.hasText(request.confirmationToken()) || !"PLAN_READY".equals(task.getStatus())
                || taskMapper.queuePlanReady(task.getId(), user.tenantId(), user.userId(), request.planVersion(),
                request.planHash(), LocalDateTime.now().plusNanos(
                        runtime.getLimits().getDefaultTaskTimeoutMs() * 1_000_000L)) != 1)
            throw new BusinessException(ErrorCode.INVALID_TASK_STATE);
        eventService.publish(task.getId(), "snapshot", objectMapper.createObjectNode()
                .put("taskId", taskNo).put("status", "QUEUED"), task.getTraceId());
        applicationEventPublisher.publishEvent(new AgentTaskQueuedEvent(task.getId()));
        return executionResponse(taskNo, "QUEUED");
    }

    private BuiltPlan buildPlan(long taskId, String traceId, PlannerCandidate candidate,
                                Map<String, ToolDefinition> definitions) {
        ObjectNode plan = objectMapper.createObjectNode().put("planVersion", 1).put("summary", candidate.summary());
        var planSteps = plan.putArray("steps");
        List<AgentTaskStep> steps = new ArrayList<>();
        RiskLevel maxRisk = RiskLevel.L0;
        for (int index = 0; index < candidate.steps().size(); index++) {
            PlannerCandidate.Step candidateStep = candidate.steps().get(index);
            ToolDefinition definition = definitions.get(candidateStep.toolCode());
            int sequence = index + 1;
            String argsHash = hashing.hash(candidateStep.arguments());
            planSteps.addObject().put("sequence", sequence).put("toolCode", definition.code())
                    .put("toolVersion", definition.handlerVersion()).put("schemaHash", definition.schemaHash())
                    .put("argsHash", argsHash).put("riskLevel", definition.riskLevel().name())
                    .put("sideEffect", definition.sideEffect().name())
                    .put("confirmationPolicy", definition.confirmationPolicy().name());
            if (definition.riskLevel().ordinal() > maxRisk.ordinal()) maxRisk = definition.riskLevel();
            AgentTaskStep step = new AgentTaskStep();
            step.setTaskId(taskId);
            step.setSequenceNo(sequence);
            step.setToolCode(definition.code());
            step.setToolVersion(definition.handlerVersion());
            step.setSchemaHash(definition.schemaHash());
            step.setArgs(json(candidateStep.arguments()));
            step.setArgsHash(argsHash);
            step.setRiskLevel(definition.riskLevel().name());
            step.setStatus("PENDING");
            step.setAttemptCount(0);
            step.setTraceId(traceId);
            step.setVersion(0L);
            steps.add(step);
        }
        return new BuiltPlan(plan, hashing.hash(plan), maxRisk.name(), steps);
    }

    private AgentTask newTask(AiTaskPlanRequest request, JsonNode safeContext, AuthenticatedUser user) {
        AgentTask task = new AgentTask();
        task.setTaskNo(UUID.randomUUID().toString());
        task.setTenantId(user.tenantId());
        task.setUserId(user.userId());
        task.setPageId(request.getPageId());
        task.setInput(request.getInput());
        task.setPageContext(json(safeContext));
        task.setPlanVersion(1);
        task.setMaxRiskLevel("L0");
        task.setStatus("RECEIVED");
        task.setAttemptCount(0);
        task.setToolCallCount(0);
        task.setTraceId(StringUtils.hasText(TraceContext.traceId()) ? TraceContext.traceId() : UUID.randomUUID().toString());
        task.setVersion(0L);
        return task;
    }

    private AiTaskPlanResponse response(AgentTask task, List<AgentTaskStep> steps) {
        JsonNode plan = read(task.getPlan());
        return new AiTaskPlanResponse(task.getTaskNo(), task.getStatus(), task.getPlanVersion(), task.getPlanHash(),
                task.getMaxRiskLevel(), !"L0".equals(task.getMaxRiskLevel()), null,
                plan == null ? "" : plan.path("summary").asText(), steps.stream().map(step ->
                new AiTaskPlanResponse.Step(step.getSequenceNo(), step.getToolCode(), step.getToolCode(), read(step.getArgs())))
                .toList());
    }

    private AiTaskExecuteResponse executionResponse(String taskNo, String status) {
        return new AiTaskExecuteResponse(taskNo, status, "/api/ai/tasks/" + taskNo,
                "/api/ai/tasks/" + taskNo + "/events");
    }

    private void requirePlanningAvailable() {
        if (!runtime.isEnabled() || !runtime.isPlanningEnabled() || !aiRuntime.configured())
            throw new BusinessException(ErrorCode.AI_TASK_CAPABILITY_UNAVAILABLE);
    }

    private ResolvedUserAccess access(AuthenticatedUser user) {
        return new ResolvedUserAccess(user.userId(), user.username(), user.tenantId(), user.role(), user.roles(),
                user.permissions(), user.dataScopes(), user.permissionVersion());
    }

    private String json(JsonNode value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Agent JSON serialization failed", exception); }
    }
    private JsonNode read(String value) {
        if (!StringUtils.hasText(value)) return null;
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Stored Agent JSON is invalid", exception); }
    }
    private record BuiltPlan(ObjectNode plan, String planHash, String riskLevel, List<AgentTaskStep> steps) { }
}
