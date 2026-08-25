package com.aiworkmate.agent.task;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AgentTaskEventService {
    private static final long SSE_TIMEOUT_MS = 120_000L;
    private static final int REPLAY_LIMIT = 1_000;

    private final AgentTaskMapper taskMapper;
    private final AgentTaskEventMapper eventMapper;
    private final ObjectMapper objectMapper;
    private final Map<Long, Set<Subscription>> subscriptions = new ConcurrentHashMap<>();

    public SseEmitter open(Long tenantId, Long userId, String taskNo, long lastEventId) {
        AgentTask task = taskMapper.selectOwned(tenantId, userId, taskNo);
        if (task == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Subscription subscription = new Subscription(task.getId(), emitter, Math.max(0, lastEventId));
        subscriptions.computeIfAbsent(task.getId(), ignored -> new CopyOnWriteArraySet<>()).add(subscription);
        emitter.onCompletion(() -> remove(subscription));
        emitter.onTimeout(() -> remove(subscription));
        emitter.onError(ignored -> remove(subscription));

        synchronized (subscription) {
            for (AgentTaskEvent event : eventMapper.selectOwnedEvents(
                    tenantId, userId, taskNo, subscription.lastEventId, REPLAY_LIMIT)) {
                send(subscription, event);
            }
            if (AgentTaskStatus.valueOf(task.getStatus()).terminal()) {
                emitter.complete();
                remove(subscription);
            }
        }
        return emitter;
    }

    public AgentTaskEvent publish(long taskId, String eventType, JsonNode payload, String traceId) {
        AgentTaskEvent event = new AgentTaskEvent();
        event.setTaskId(taskId);
        event.setEventType(eventType);
        event.setPayload(payload == null ? "{}" : payload.toString());
        event.setTraceId(Objects.requireNonNullElse(traceId, "agent-task-event"));
        AgentTaskEvent persisted = eventMapper.insertEvent(event);
        if (persisted == null || persisted.getId() == null) {
            throw new IllegalStateException("Agent task event persistence failed");
        }
        afterCommit(() -> broadcast(persisted));
        return persisted;
    }

    @Scheduled(fixedDelayString = "${agent.events.heartbeat-delay-ms:15000}")
    public void heartbeat() {
        subscriptions.values().forEach(set -> set.forEach(subscription -> {
            synchronized (subscription) {
                try {
                    subscription.emitter.send(SseEmitter.event().name("heartbeat").data(Map.of("type", "heartbeat")));
                } catch (IOException | IllegalStateException exception) {
                    remove(subscription);
                }
            }
        }));
    }

    private void broadcast(AgentTaskEvent event) {
        Set<Subscription> current = subscriptions.get(event.getTaskId());
        if (current == null) return;
        current.forEach(subscription -> {
            synchronized (subscription) {
                send(subscription, event);
                if (terminal(event)) {
                    subscription.emitter.complete();
                    remove(subscription);
                }
            }
        });
    }

    private boolean terminal(AgentTaskEvent event) {
        if ("task-completed".equals(event.getEventType()) || "task-failed".equals(event.getEventType())) return true;
        if (!"snapshot".equals(event.getEventType())) return false;
        try {
            JsonNode status = objectMapper.readTree(event.getPayload()).get("status");
            return status != null && AgentTaskStatus.valueOf(status.asText()).terminal();
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }
    }

    private void send(Subscription subscription, AgentTaskEvent event) {
        if (event.getId() == null || event.getId() <= subscription.lastEventId) return;
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            subscription.emitter.send(SseEmitter.event()
                    .id(String.valueOf(event.getId()))
                    .name(event.getEventType())
                    .reconnectTime(3_000L)
                    .data(payload));
            subscription.lastEventId = event.getId();
        } catch (IOException | IllegalStateException exception) {
            remove(subscription);
        }
    }

    private void remove(Subscription subscription) {
        Set<Subscription> current = subscriptions.get(subscription.taskId);
        if (current == null) return;
        current.remove(subscription);
        if (current.isEmpty()) subscriptions.remove(subscription.taskId, current);
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private static final class Subscription {
        private final long taskId;
        private final SseEmitter emitter;
        private long lastEventId;

        private Subscription(long taskId, SseEmitter emitter, long lastEventId) {
            this.taskId = taskId;
            this.emitter = emitter;
            this.lastEventId = lastEventId;
        }
    }
}
