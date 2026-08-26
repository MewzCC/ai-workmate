package com.aiworkmate.agent.task;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class AgentTaskStateMachine {
    private static final Map<AgentTaskStatus, Set<AgentTaskStatus>> TRANSITIONS = transitions();

    public boolean canTransition(AgentTaskStatus from, AgentTaskStatus to) {
        return from != null && to != null && TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public void requireTransition(AgentTaskStatus from, AgentTaskStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Illegal Agent task transition: " + from + " -> " + to);
        }
    }

    public boolean cancellable(AgentTaskStatus status) {
        return status == AgentTaskStatus.PLAN_READY
                || status == AgentTaskStatus.WAITING_CONFIRMATION
                || status == AgentTaskStatus.QUEUED;
    }

    private static Map<AgentTaskStatus, Set<AgentTaskStatus>> transitions() {
        Map<AgentTaskStatus, Set<AgentTaskStatus>> transitions = new EnumMap<>(AgentTaskStatus.class);
        transitions.put(AgentTaskStatus.RECEIVED, EnumSet.of(AgentTaskStatus.PLANNING));
        transitions.put(AgentTaskStatus.PLANNING, EnumSet.of(
                AgentTaskStatus.PLAN_READY, AgentTaskStatus.WAITING_CONFIRMATION, AgentTaskStatus.REJECTED
        ));
        transitions.put(AgentTaskStatus.PLAN_READY, EnumSet.of(AgentTaskStatus.QUEUED, AgentTaskStatus.CANCELLED));
        transitions.put(AgentTaskStatus.WAITING_CONFIRMATION, EnumSet.of(
                AgentTaskStatus.QUEUED, AgentTaskStatus.EXPIRED, AgentTaskStatus.CANCELLED
        ));
        transitions.put(AgentTaskStatus.QUEUED, EnumSet.of(AgentTaskStatus.RUNNING, AgentTaskStatus.CANCELLED));
        transitions.put(AgentTaskStatus.RUNNING, EnumSet.of(
                AgentTaskStatus.SUCCEEDED, AgentTaskStatus.PARTIALLY_SUCCEEDED, AgentTaskStatus.FAILED,
                AgentTaskStatus.TIMED_OUT
        ));
        return Map.copyOf(transitions);
    }
}
