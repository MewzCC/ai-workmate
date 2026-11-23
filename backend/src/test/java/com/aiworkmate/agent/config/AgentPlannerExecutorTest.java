package com.aiworkmate.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentPlannerExecutorTest {
    @Test void startsTwoPlansWithoutSerialQueueingAndRejectsOverload() throws Exception {
        var executor = (ThreadPoolTaskExecutor) new AgentExecutionConfig().agentPlannerExecutor();
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        Runnable slow = () -> {
            started.countDown();
            try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        };
        try {
            executor.execute(slow);
            executor.execute(slow);
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> executor.execute(slow))
                    .isInstanceOf(org.springframework.core.task.TaskRejectedException.class);
        } finally { release.countDown(); executor.shutdown(); }
    }
}
