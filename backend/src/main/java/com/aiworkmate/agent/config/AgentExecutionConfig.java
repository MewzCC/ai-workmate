package com.aiworkmate.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AgentExecutionConfig {

    @Bean("agentTaskExecutor")
    public TaskExecutor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("agent-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @Bean("agentPlannerExecutor")
    public java.util.concurrent.Executor agentPlannerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("agent-planner-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(value = "agentToolExecutor", destroyMethod = "shutdown")
    public ExecutorService agentToolExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        return new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8),
                runnable -> {
                    Thread thread = new Thread(runnable, "agent-tool-" + sequence.incrementAndGet());
                    thread.setDaemon(false);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
