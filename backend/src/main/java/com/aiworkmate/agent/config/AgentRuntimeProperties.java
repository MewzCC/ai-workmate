package com.aiworkmate.agent.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentRuntimeProperties {

    private boolean enabled;
    private boolean planningEnabled;
    private boolean executionEnabled;
    private boolean writeToolsEnabled;

    @Valid
    private Limits limits = new Limits();

    @Data
    public static class Limits {
        @Min(1)
        @Max(4096)
        private int inputMaxBytes = 4096;

        @Min(1)
        @Max(16384)
        private int pageContextMaxBytes = 16384;

        @Min(1)
        @Max(3)
        private int pageContextMaxDepth = 3;

        @Min(1)
        @Max(3)
        private int maxPlanSteps = 3;

        @Min(100)
        @Max(15000)
        private int plannerTimeoutMs = 15000;

        @Min(1)
        @Max(5)
        private int maxToolCalls = 5;

        @Min(1)
        @Max(2)
        private int maxConcurrentTasksPerUser = 2;

        @Min(1)
        @Max(20)
        private int defaultQuerySize = 20;

        @Min(1)
        @Max(50)
        private int maxQuerySize = 50;

        @Min(1)
        @Max(5)
        private int defaultKnowledgeTopK = 5;

        @Min(1)
        @Max(10)
        private int maxKnowledgeTopK = 10;

        @Min(1024)
        @Max(262144)
        private int maxStepResultBytes = 262144;

        @Min(1000)
        @Max(30000)
        private int defaultToolTimeoutMs = 15000;

        @Min(1000)
        @Max(30000)
        private int maxToolTimeoutMs = 30000;

        @Min(1000)
        @Max(120000)
        private int defaultTaskTimeoutMs = 60000;

        @Min(1000)
        @Max(120000)
        private int maxTaskTimeoutMs = 120000;

        @Min(60)
        @Max(600)
        private int confirmationTokenTtlSeconds = 600;

        @Min(1)
        @Max(90)
        private int taskRetentionDays = 90;

        @Min(1)
        @Max(30)
        private int eventRetentionDays = 30;
    }
}
