package com.aiworkmate.agent.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTaskStateMachineTest {
    private final AgentTaskStateMachine stateMachine = new AgentTaskStateMachine();

    @Test
    void shouldAllowOnlyFrozenTransitions() {
        assertThat(stateMachine.canTransition(AgentTaskStatus.RECEIVED, AgentTaskStatus.PLANNING)).isTrue();
        assertThat(stateMachine.canTransition(AgentTaskStatus.PLANNING, AgentTaskStatus.PLAN_READY)).isTrue();
        assertThat(stateMachine.canTransition(AgentTaskStatus.PLAN_READY, AgentTaskStatus.QUEUED)).isTrue();
        assertThat(stateMachine.canTransition(AgentTaskStatus.QUEUED, AgentTaskStatus.RUNNING)).isTrue();
        assertThat(stateMachine.canTransition(AgentTaskStatus.RUNNING, AgentTaskStatus.SUCCEEDED)).isTrue();
    }

    @Test
    void shouldRejectSkippingOrLeavingTerminalStates() {
        assertThatThrownBy(() -> stateMachine.requireTransition(AgentTaskStatus.RECEIVED, AgentTaskStatus.RUNNING))
                .isInstanceOf(IllegalStateException.class);
        assertThat(stateMachine.canTransition(AgentTaskStatus.SUCCEEDED, AgentTaskStatus.RUNNING)).isFalse();
        assertThat(AgentTaskStatus.SUCCEEDED.terminal()).isTrue();
    }

    @Test
    void shouldLimitCancellationToSafePreExecutionStates() {
        assertThat(stateMachine.cancellable(AgentTaskStatus.PLAN_READY)).isTrue();
        assertThat(stateMachine.cancellable(AgentTaskStatus.WAITING_CONFIRMATION)).isTrue();
        assertThat(stateMachine.cancellable(AgentTaskStatus.QUEUED)).isTrue();
        assertThat(stateMachine.cancellable(AgentTaskStatus.RUNNING)).isFalse();
    }
}
