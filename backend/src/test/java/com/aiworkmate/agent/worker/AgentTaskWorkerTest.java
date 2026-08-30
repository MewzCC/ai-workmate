package com.aiworkmate.agent.worker;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.gateway.GatewayDecision;
import com.aiworkmate.agent.gateway.GatewayDecisionCode;
import com.aiworkmate.agent.gateway.ToolGateway;
import com.aiworkmate.agent.gateway.ToolGatewayResult;
import com.aiworkmate.agent.observability.AgentOperationalObserver;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.task.AgentTask;
import com.aiworkmate.agent.task.AgentTaskStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTaskWorkerTest {
    private final AgentWorkerMapper mapper = mock(AgentWorkerMapper.class);
    private final ToolGateway gateway = mock(ToolGateway.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentWorkerTransitionService transitions = mock(AgentWorkerTransitionService.class);
    private final AgentOperationalObserver observer = mock(AgentOperationalObserver.class);
    private final AgentRuntimeProperties properties = new AgentRuntimeProperties();
    private AgentTaskWorker worker;

    @BeforeEach
    void setUp() {
        worker = new AgentTaskWorker(properties, mapper, gateway,
                new AgentHashing(objectMapper), objectMapper, transitions, observer, new SyncTaskExecutor());
    }

    @Test
    void killSwitchPreventsQueueClaimAndRecovery() {
        worker.poll();
        worker.heartbeatAndRecover();
        verify(mapper, never()).claim(anyString(), anyString(), any(), anyInt());
        verify(mapper, never()).recoverExpiredReadOnly();
        verify(observer, never()).workerRecovery(anyInt(), anyInt(), anyInt());
    }

    @Test
    void claimsExecutesThroughGatewayAndCompletesTask() {
        properties.setEnabled(true);
        properties.setExecutionEnabled(true);
        AgentTask task = task();
        AgentTaskStep step = step();
        when(mapper.claim(anyString(), anyString(), any(), anyInt())).thenReturn(task);
        when(transitions.startNextStep(any(), anyString(), anyString(), any()))
                .thenReturn(step).thenReturn(null);
        when(gateway.execute(anyLong(), any())).thenReturn(new ToolGatewayResult(
                GatewayDecision.ALLOW, GatewayDecisionCode.ALLOWED,
                objectMapper.createObjectNode().put("ok", true)));
        when(transitions.completeStep(any(), any(), anyString(), anyString(), anyString())).thenReturn(true);

        worker.poll();

        verify(gateway).execute(org.mockito.ArgumentMatchers.eq(20L), any());
        verify(transitions).completeTask(any(), anyString(), anyString());
    }

    @Test
    void unavailableReadOnlyStepIsAtomicallyRequeued() {
        properties.setEnabled(true);
        properties.setExecutionEnabled(true);
        AgentTask task = task();
        AgentTaskStep step = step();
        when(mapper.claim(anyString(), anyString(), any(), anyInt())).thenReturn(task);
        when(transitions.startNextStep(any(), anyString(), anyString(), any())).thenReturn(step);
        when(gateway.execute(anyLong(), any())).thenReturn(ToolGatewayResult.unavailable(
                GatewayDecisionCode.GATEWAY_UNAVAILABLE));
        when(transitions.retryReadOnly(any(), any(), anyString(), anyString())).thenReturn(true);

        worker.poll();

        verify(transitions).retryReadOnly(any(), any(), anyString(), anyString());
        verify(transitions, never()).fail(any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void unknownWriteOutcomeIsClosedForManualVerificationWithoutRetry() {
        properties.setEnabled(true);
        properties.setExecutionEnabled(true);
        AgentTask task = task();
        AgentTaskStep step = step();
        step.setRiskLevel("L2");
        when(mapper.claim(anyString(), anyString(), any(), anyInt())).thenReturn(task);
        when(transitions.startNextStep(any(), anyString(), anyString(), any())).thenReturn(step);
        when(gateway.execute(anyLong(), any())).thenReturn(ToolGatewayResult.uncertain());

        worker.poll();

        verify(transitions).markOutcomeUnknown(any(), any(), anyString(), anyString());
        verify(transitions, never()).retryReadOnly(any(), any(), anyString(), anyString());
        verify(transitions, never()).fail(any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void recoveryClosesUnsafeWorkBeforeRequeuingSafeExpiredLeases() {
        properties.setEnabled(true);
        properties.setExecutionEnabled(true);

        worker.heartbeatAndRecover();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(mapper);
        order.verify(mapper).closeTimedOutOrUnsafe();
        order.verify(mapper).recoverExpiredReadOnly();
        verify(observer).workerRecovery(0, 0, 0);
    }

    @Test
    void executorRejectionDoesNotLeakLocalConcurrencyCapacity() {
        properties.setEnabled(true);
        properties.setExecutionEnabled(true);
        when(mapper.claim(anyString(), anyString(), any(), anyInt())).thenReturn(task());
        worker = new AgentTaskWorker(properties, mapper, gateway,
                new AgentHashing(objectMapper), objectMapper, transitions,
                observer,
                command -> { throw new TaskRejectedException("saturated"); });

        worker.poll();
        worker.poll();

        verify(mapper, times(2)).claim(anyString(), anyString(), any(), anyInt());
    }

    private AgentTask task() {
        AgentTask task = new AgentTask();
        task.setId(10L);
        task.setAttemptCount(0);
        task.setTaskNo("00000000-0000-4000-8000-000000000010");
        task.setTraceId("trace-worker-test");
        return task;
    }

    private AgentTaskStep step() {
        AgentTaskStep step = new AgentTaskStep();
        step.setId(20L);
        step.setRiskLevel("L0");
        step.setSequenceNo(1);
        step.setToolCode("todo.query");
        step.setTraceId("trace-worker-step-test");
        return step;
    }
}
