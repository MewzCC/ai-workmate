package com.aiworkmate.agent.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRuntimePropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRemainFailClosedByDefault() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isPlanningEnabled()).isFalse();
        assertThat(properties.isExecutionEnabled()).isFalse();
        assertThat(properties.isWriteToolsEnabled()).isFalse();
        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void shouldRejectLimitsAbovePlatformCeilings() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getLimits().setMaxPlanSteps(4);
        properties.getLimits().setMaxQuerySize(51);
        properties.getLimits().setMaxToolTimeoutMs(30001);
        properties.setRetentionBatchSize(1001);

        assertThat(validator.validate(properties)).hasSize(4);
    }
}
