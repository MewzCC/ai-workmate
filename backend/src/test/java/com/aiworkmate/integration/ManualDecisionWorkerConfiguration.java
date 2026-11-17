package com.aiworkmate.integration;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.gateway.ToolGateway;
import com.aiworkmate.agent.observability.AgentOperationalObserver;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.task.AgentTaskQueuedEvent;
import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.aiworkmate.agent.worker.AgentWorkerMapper;
import com.aiworkmate.agent.worker.AgentWorkerTransitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;

/** Real Worker/Gateway execution, triggered explicitly by decision fixtures instead of a mutable shared spy. */
@TestConfiguration(proxyBeanMethods = false)
class ManualDecisionWorkerConfiguration {
    @Bean("agentTaskWorker")
    AgentTaskWorker manualDecisionWorker(AgentRuntimeProperties properties, AgentWorkerMapper mapper,
            ToolGateway gateway, AgentHashing hashing, ObjectMapper json,
            AgentWorkerTransitionService transitions, AgentOperationalObserver observer,
            @Qualifier("agentTaskExecutor") TaskExecutor executor) {
        return new AgentTaskWorker(properties, mapper, gateway, hashing, json, transitions, observer, executor) {
            @Override public void wakeAfterCommit(AgentTaskQueuedEvent event) {
                // Confirmation fixtures call the inherited real poll() at a deterministic point.
            }
        };
    }
}
