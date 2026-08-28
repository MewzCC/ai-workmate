package com.aiworkmate.agent.gateway;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.registry.PermissionMode;
import com.aiworkmate.agent.registry.RiskLevel;
import com.aiworkmate.agent.registry.SideEffect;
import com.aiworkmate.agent.registry.ToolDefinition;
import com.aiworkmate.agent.registry.ToolRegistry;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.tool.internal.ToolHandler;
import com.aiworkmate.agent.tool.internal.TrustedToolContext;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class DefaultToolGateway implements ToolGateway {
    private final AgentRuntimeProperties runtimeProperties;
    private final GatewayExecutionSnapshotMapper snapshotMapper;
    private final ToolRegistry toolRegistry;
    private final UserAccessService userAccessService;
    private final AgentHashing hashing;
    private final ToolSchemaValidator schemaValidator;
    private final ToolOutputGuard outputGuard;
    private final HandlerResolver handlerResolver;
    private final GatewayAuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    private final ExecutorService toolExecutor;

    public DefaultToolGateway(AgentRuntimeProperties runtimeProperties,
                              GatewayExecutionSnapshotMapper snapshotMapper,
                              ToolRegistry toolRegistry,
                              UserAccessService userAccessService,
                              AgentHashing hashing,
                              ToolSchemaValidator schemaValidator,
                              ToolOutputGuard outputGuard,
                              HandlerResolver handlerResolver,
                              GatewayAuditWriter auditWriter,
                              ObjectMapper objectMapper,
                              @Qualifier("agentToolExecutor") ExecutorService toolExecutor) {
        this.runtimeProperties = runtimeProperties;
        this.snapshotMapper = snapshotMapper;
        this.toolRegistry = toolRegistry;
        this.userAccessService = userAccessService;
        this.hashing = hashing;
        this.schemaValidator = schemaValidator;
        this.outputGuard = outputGuard;
        this.handlerResolver = handlerResolver;
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
        this.toolExecutor = toolExecutor;
    }

    @Override
    public ToolGatewayResult execute(long stepId, WorkerLease lease) {
        if (!runtimeProperties.isEnabled() || !runtimeProperties.isExecutionEnabled()) {
            return ToolGatewayResult.unavailable(GatewayDecisionCode.GATEWAY_DISABLED);
        }
        if (stepId <= 0 || lease == null) {
            return reject(GatewayDecision.DENY, GatewayDecisionCode.GATEWAY_DENIED);
        }

        GatewayExecutionSnapshot snapshot;
        ToolDefinition definition;
        JsonNode plan;
        JsonNode arguments;
        try {
            snapshot = snapshotMapper.selectSnapshot(stepId);
            if (snapshot == null) {
                return reject(GatewayDecision.DENY, GatewayDecisionCode.GATEWAY_DENIED);
            }
            definition = toolRegistry.resolveExecutableTool(snapshot.getTenantId(), snapshot.getToolCode()).orElse(null);
            if (definition == null) {
                return auditedReject(snapshot, GatewayDecision.DENY, GatewayDecisionCode.GATEWAY_DENIED, "TOOL_DISABLED");
            }
            if (!validLeaseAndState(snapshot, lease)) {
                return auditedReject(snapshot, GatewayDecision.STALE, GatewayDecisionCode.GATEWAY_STALE, "LEASE_OR_STATE_STALE");
            }
            plan = objectMapper.readTree(snapshot.getPlan());
            arguments = objectMapper.readTree(snapshot.getArguments());
            if (!validHashes(snapshot, definition, plan, arguments)) {
                return auditedReject(snapshot, GatewayDecision.STALE, GatewayDecisionCode.GATEWAY_STALE, "SNAPSHOT_HASH_STALE");
            }
        } catch (RuntimeException | JsonProcessingException exception) {
            return reject(GatewayDecision.UNAVAILABLE, GatewayDecisionCode.GATEWAY_UNAVAILABLE);
        }

        ResolvedUserAccess access;
        try {
            access = userAccessService.resolveActiveUser(snapshot.getUserId());
        } catch (RuntimeException exception) {
            return auditedReject(snapshot, GatewayDecision.UNAVAILABLE, GatewayDecisionCode.GATEWAY_UNAVAILABLE, "RBAC_UNAVAILABLE");
        }
        try {
            if (!validAccess(snapshot, definition, access)) {
                return auditedReject(snapshot, GatewayDecision.DENY, GatewayDecisionCode.GATEWAY_DENIED,
                        "REALTIME_PERMISSION_DENIED");
            }
        } catch (RuntimeException exception) {
            return auditedReject(snapshot, GatewayDecision.UNAVAILABLE, GatewayDecisionCode.GATEWAY_UNAVAILABLE,
                    "RBAC_RESULT_INVALID");
        }
        if (!validRiskAndConfirmation(snapshot, definition)) {
            return auditedReject(snapshot, GatewayDecision.DENY, GatewayDecisionCode.GATEWAY_DENIED, "RISK_OR_CONFIRMATION_DENIED");
        }
        int maxCalls = runtimeProperties.getLimits().getMaxToolCalls();
        if (snapshot.getToolCallCount() == null || snapshot.getToolCallCount() >= maxCalls) {
            return auditedReject(snapshot, GatewayDecision.THROTTLED, GatewayDecisionCode.GATEWAY_THROTTLED, "CALL_BUDGET_EXHAUSTED");
        }
        try {
            if (!schemaValidator.valid(definition.inputSchema(), arguments)) {
                return auditedReject(snapshot, GatewayDecision.DENY, GatewayDecisionCode.GATEWAY_DENIED,
                        "INPUT_SCHEMA_REJECTED");
            }
        } catch (RuntimeException exception) {
            return auditedReject(snapshot, GatewayDecision.UNAVAILABLE, GatewayDecisionCode.GATEWAY_UNAVAILABLE,
                    "SCHEMA_VALIDATOR_UNAVAILABLE");
        }
        try {
            if (snapshotMapper.reserveToolCall(
                    snapshot.getTaskId(), snapshot.getStepId(), lease.workerId(), snapshot.getLeaseTokenHash(),
                    lease.attempt(), maxCalls
            ) != 1) {
                return auditedReject(snapshot, GatewayDecision.THROTTLED, GatewayDecisionCode.GATEWAY_THROTTLED,
                        "CALL_BUDGET_RACE_LOST");
            }
        } catch (RuntimeException exception) {
            return auditedReject(snapshot, GatewayDecision.UNAVAILABLE, GatewayDecisionCode.GATEWAY_UNAVAILABLE,
                    "BUDGET_UNAVAILABLE");
        }

        String decisionId;
        try {
            decisionId = auditWriter.record(snapshot, GatewayDecision.ALLOW, "POLICY_ALLOWED", false);
        } catch (RuntimeException exception) {
            return reject(GatewayDecision.UNAVAILABLE, GatewayDecisionCode.GATEWAY_UNAVAILABLE);
        }

        ToolHandler handler = handlerResolver.resolve(snapshot.getToolCode(), snapshot.getToolVersion()).orElse(null);
        if (handler == null) {
            completeQuietly(decisionId, false, "FAILED", null, "HANDLER_UNAVAILABLE", 0);
            return reject(GatewayDecision.UNAVAILABLE, GatewayDecisionCode.GATEWAY_UNAVAILABLE);
        }

        long started = System.nanoTime();
        JsonNode output;
        Future<JsonNode> call;
        try {
            call = toolExecutor.submit(() -> handler.execute(new TrustedToolContext(
                    snapshot.getTenantId(), snapshot.getUserId(), snapshot.getTaskId(), snapshot.getStepId(),
                    snapshot.getStepAttempt(), snapshot.getTraceId()
            ), arguments));
        } catch (RejectedExecutionException exception) {
            completeQuietly(decisionId, false, "FAILED", null, "TOOL_EXECUTOR_UNAVAILABLE", elapsedMillis(started));
            return reject(GatewayDecision.UNAVAILABLE, GatewayDecisionCode.GATEWAY_UNAVAILABLE);
        }
        try {
            output = call.get(executionTimeoutMs(snapshot, definition), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            call.cancel(true);
            completeQuietly(decisionId, true, "TIMED_OUT", null, "TOOL_TIMEOUT", elapsedMillis(started));
            return postInvocationFailure(definition);
        } catch (InterruptedException exception) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            completeQuietly(decisionId, true, "FAILED", null, "WORKER_INTERRUPTED", elapsedMillis(started));
            return postInvocationFailure(definition);
        } catch (ExecutionException exception) {
            completeQuietly(decisionId, true, "FAILED", null, "DOMAIN_OR_HANDLER_FAILURE", elapsedMillis(started));
            return postInvocationFailure(definition);
        }

        int resultBytes;
        try {
            resultBytes = output == null ? 0 : objectMapper.writeValueAsBytes(output).length;
        } catch (RuntimeException | JsonProcessingException exception) {
            completeQuietly(decisionId, true, "RESULT_INVALID", null, "OUTPUT_SERIALIZATION_REJECTED",
                    elapsedMillis(started));
            return definition.sideEffect() == SideEffect.NONE
                    ? reject(GatewayDecision.DENY, GatewayDecisionCode.TOOL_RESULT_INVALID)
                    : ToolGatewayResult.uncertain();
        }
        boolean outputAccepted;
        try {
            outputAccepted = resultBytes <= Math.min(
                    definition.maxResultBytes(), runtimeProperties.getLimits().getMaxStepResultBytes()
            ) && schemaValidator.valid(definition.outputSchema(), output)
                    && outputGuard.withinArrayLimit(output, definition.maxResultItems())
                    && outputGuard.safe(output);
        } catch (RuntimeException exception) {
            outputAccepted = false;
        }
        if (!outputAccepted) {
            completeQuietly(decisionId, true, "RESULT_INVALID", resultBytes, "OUTPUT_REJECTED", elapsedMillis(started));
            return definition.sideEffect() == SideEffect.NONE
                    ? reject(GatewayDecision.DENY, GatewayDecisionCode.TOOL_RESULT_INVALID)
                    : ToolGatewayResult.uncertain();
        }
        try {
            auditWriter.complete(decisionId, true, "SUCCEEDED", resultBytes, null, elapsedMillis(started));
        } catch (RuntimeException exception) {
            return postInvocationFailure(definition);
        }
        return new ToolGatewayResult(GatewayDecision.ALLOW, GatewayDecisionCode.ALLOWED, output);
    }

    private boolean validLeaseAndState(GatewayExecutionSnapshot snapshot, WorkerLease lease) {
        LocalDateTime now = LocalDateTime.now();
        return "RUNNING".equals(snapshot.getTaskStatus())
                && "RUNNING".equals(snapshot.getStepStatus())
                && lease.workerId().equals(snapshot.getWorkerId())
                && snapshot.getLeaseUntil() != null
                && snapshot.getLeaseUntil().isAfter(now)
                && snapshot.getTaskTimeoutAt() != null
                && snapshot.getTaskTimeoutAt().isAfter(now)
                && snapshot.getStepTimeoutAt() != null
                && snapshot.getStepTimeoutAt().isAfter(now)
                && snapshot.getTaskAttempt() != null
                && snapshot.getStepAttempt() != null
                && lease.attempt() == snapshot.getTaskAttempt()
                && lease.attempt() == snapshot.getStepAttempt()
                && secureEquals(hashing.sha256(lease.leaseToken()), snapshot.getLeaseTokenHash());
    }

    private long executionTimeoutMs(GatewayExecutionSnapshot snapshot, ToolDefinition definition) {
        LocalDateTime now = LocalDateTime.now();
        long stepRemaining = Math.max(1, Duration.between(now, snapshot.getStepTimeoutAt()).toMillis());
        long taskRemaining = Math.max(1, Duration.between(now, snapshot.getTaskTimeoutAt()).toMillis());
        return Math.max(1, Math.min(
                Math.min(definition.timeoutMs(), runtimeProperties.getLimits().getMaxToolTimeoutMs()),
                Math.min(stepRemaining, taskRemaining)
        ));
    }

    private boolean validHashes(GatewayExecutionSnapshot snapshot,
                                ToolDefinition definition,
                                JsonNode plan,
                                JsonNode arguments) {
        return snapshot.getPlanVersion() != null
                && snapshot.getPlanVersion() >= 1
                && secureEquals(hashing.hash(plan), snapshot.getPlanHash())
                && definition.handlerVersion().equals(snapshot.getToolVersion())
                && definition.schemaHash().equals(snapshot.getSchemaHash())
                && secureEquals(hashing.hash(arguments), snapshot.getArgsHash())
                && definition.riskLevel().name().equals(snapshot.getStepRiskLevel())
                && riskAtLeast(snapshot.getTaskRiskLevel(), definition.riskLevel())
                && validPlanBinding(snapshot, definition, plan);
    }

    private boolean validPlanBinding(GatewayExecutionSnapshot snapshot,
                                     ToolDefinition definition,
                                     JsonNode plan) {
        if (snapshot.getSequenceNo() == null || snapshot.getSequenceNo() < 1
                || plan.path("planVersion").asInt(-1) != snapshot.getPlanVersion()) {
            return false;
        }
        JsonNode steps = plan.path("steps");
        if (!steps.isArray() || steps.isEmpty()
                || steps.size() > runtimeProperties.getLimits().getMaxPlanSteps()
                || snapshot.getSequenceNo() > steps.size()) {
            return false;
        }
        JsonNode planned = steps.get(snapshot.getSequenceNo() - 1);
        int writeSteps = 0;
        for (JsonNode step : steps) {
            if (SideEffect.SINGLE_WRITE.name().equals(step.path("sideEffect").asText())) {
                writeSteps++;
            }
        }
        return planned.path("sequence").asInt(-1) == snapshot.getSequenceNo()
                && snapshot.getToolCode().equals(planned.path("toolCode").asText())
                && snapshot.getToolVersion().equals(planned.path("toolVersion").asText())
                && snapshot.getSchemaHash().equals(planned.path("schemaHash").asText())
                && snapshot.getArgsHash().equals(planned.path("argsHash").asText())
                && definition.riskLevel().name().equals(planned.path("riskLevel").asText())
                && definition.sideEffect().name().equals(planned.path("sideEffect").asText())
                && definition.confirmationPolicy().name().equals(planned.path("confirmationPolicy").asText())
                && writeSteps <= 1;
    }

    private boolean validAccess(GatewayExecutionSnapshot snapshot,
                                ToolDefinition definition,
                                ResolvedUserAccess access) {
        if (access == null || !snapshot.getTenantId().equals(access.tenantId())
                || !snapshot.getUserId().equals(access.userId())) {
            return false;
        }
        return definition.permissionMode() == PermissionMode.ALL
                ? access.permissions().containsAll(definition.requiredPermissions())
                : definition.requiredPermissions().stream().anyMatch(access.permissions()::contains);
    }

    private boolean validRiskAndConfirmation(GatewayExecutionSnapshot snapshot, ToolDefinition definition) {
        if (definition.sideEffect() != SideEffect.NONE && !runtimeProperties.isWriteToolsEnabled()) {
            return false;
        }
        return definition.riskLevel() == RiskLevel.L0 || snapshot.getConfirmationConsumedAt() != null;
    }

    private boolean riskAtLeast(String taskRiskLevel, RiskLevel stepRiskLevel) {
        try {
            return RiskLevel.valueOf(taskRiskLevel).ordinal() >= stepRiskLevel.ordinal();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private ToolGatewayResult auditedReject(GatewayExecutionSnapshot snapshot,
                                            GatewayDecision decision,
                                            GatewayDecisionCode publicCode,
                                            String internalCode) {
        try {
            String decisionId = auditWriter.record(snapshot, decision, internalCode, false);
            auditWriter.complete(decisionId, false, "REJECTED", null, null, 0);
            return reject(decision, publicCode);
        } catch (RuntimeException exception) {
            return reject(GatewayDecision.UNAVAILABLE, GatewayDecisionCode.GATEWAY_UNAVAILABLE);
        }
    }

    private void completeQuietly(String decisionId,
                                 boolean handlerInvoked,
                                 String outcome,
                                 Integer resultBytes,
                                 String errorClass,
                                 long durationMs) {
        try {
            auditWriter.complete(decisionId, handlerInvoked, outcome, resultBytes, errorClass, durationMs);
        } catch (RuntimeException ignored) {
            // The pre-execution ALLOW record remains durable; callers receive a fail-closed result.
        }
    }

    private boolean secureEquals(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private ToolGatewayResult reject(GatewayDecision decision, GatewayDecisionCode code) {
        return new ToolGatewayResult(decision, code, null);
    }

    private ToolGatewayResult postInvocationFailure(ToolDefinition definition) {
        return definition.sideEffect() == SideEffect.NONE
                ? reject(GatewayDecision.UNAVAILABLE, GatewayDecisionCode.GATEWAY_UNAVAILABLE)
                : ToolGatewayResult.uncertain();
    }
}
