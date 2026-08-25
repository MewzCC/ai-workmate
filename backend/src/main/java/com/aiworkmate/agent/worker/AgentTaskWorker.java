package com.aiworkmate.agent.worker;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.gateway.GatewayDecision;
import com.aiworkmate.agent.gateway.ToolGateway;
import com.aiworkmate.agent.gateway.WorkerLease;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.task.AgentTask;
import com.aiworkmate.agent.task.AgentTaskStep;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AgentTaskWorker {
    private final String workerId = "worker-" + java.util.UUID.randomUUID();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<Long, ActiveLease> activeLeases = new ConcurrentHashMap<>();
    private final AtomicInteger activeCount = new AtomicInteger();
    private final AgentRuntimeProperties properties;
    private final AgentWorkerMapper mapper;
    private final ToolGateway gateway;
    private final AgentHashing hashing;
    private final ObjectMapper objectMapper;
    private final TaskExecutor executor;

    public AgentTaskWorker(AgentRuntimeProperties properties, AgentWorkerMapper mapper,
                           ToolGateway gateway, AgentHashing hashing, ObjectMapper objectMapper,
                           @Qualifier("agentTaskExecutor") TaskExecutor executor) {
        this.properties = properties;
        this.mapper = mapper;
        this.gateway = gateway;
        this.hashing = hashing;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${agent.worker.poll-delay-ms:500}")
    public void poll() {
        if (!properties.isEnabled() || !properties.isExecutionEnabled() || activeCount.get() >= 2) return;
        String token = token();
        String hash = hashing.sha256(token);
        AgentTask task = mapper.claim(workerId, hash, LocalDateTime.now().plusSeconds(30),
                properties.getLimits().getMaxConcurrentTasksPerUser());
        if (task == null) return;
        activeCount.incrementAndGet();
        activeLeases.put(task.getId(), new ActiveLease(hash));
        try {
            executor.execute(() -> run(task, token, hash));
        } catch (RuntimeException exception) {
            activeLeases.remove(task.getId());
            activeCount.decrementAndGet();
            // The persisted lease is deliberately left for the recovery pass.
        }
    }

    @Scheduled(fixedDelayString = "${agent.worker.heartbeat-delay-ms:5000}")
    public void heartbeatAndRecover() {
        if (!properties.isEnabled() || !properties.isExecutionEnabled()) return;
        mapper.closeTimedOutOrUnsafe();
        mapper.recoverExpiredReadOnly();
        activeLeases.forEach((taskId, lease) ->
                mapper.heartbeat(taskId, workerId, lease.hash(), LocalDateTime.now().plusSeconds(30)));
    }

    void run(AgentTask task, String token, String hash) {
        try {
            WorkerLease lease = new WorkerLease(workerId, task.getAttemptCount(), token);
            while (true) {
                AgentTaskStep step = mapper.startNextStep(task.getId(), workerId, hash,
                        task.getAttemptCount(), LocalDateTime.now().plus(
                                java.time.Duration.ofMillis(properties.getLimits().getDefaultToolTimeoutMs())));
                if (step == null) {
                    mapper.completeTask(task.getId(), workerId, hash);
                    return;
                }
                var result = gateway.execute(step.getId(), lease);
                if (result.decision() == GatewayDecision.ALLOW) {
                    if (mapper.completeStep(step.getId(), task.getAttemptCount(), workerId, hash,
                            json(result.output())) != 1) return;
                    continue;
                }
                if (result.decision() == GatewayDecision.UNAVAILABLE
                        && mapper.retryReadOnly(step.getId(), task.getAttemptCount(), workerId, hash) == 1) return;
                mapper.fail(step.getId(), task.getAttemptCount(), workerId, hash,
                        result.code().name(), "Tool execution rejected");
                return;
            }
        } catch (RuntimeException exception) {
            // Lease recovery owns the outcome after unexpected process or infrastructure failures.
        } finally {
            activeLeases.remove(task.getId());
            activeCount.decrementAndGet();
        }
    }

    private String token() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String json(com.fasterxml.jackson.databind.JsonNode output) {
        try { return objectMapper.writeValueAsString(output); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }

    private record ActiveLease(String hash) { }
}
