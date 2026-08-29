package com.aiworkmate.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SaveRouteRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void allowsRegisteredAgentTaskCenterComponent() {
        SaveRouteRequest request = new SaveRouteRequest(
                "ai-tasks", "workspace", "AI 任务中心", "/oa/ai-tasks", "ai-tasks",
                "PAGE", "AI_TASK_CENTER", 3, true);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void allowsRegisteredEmployeeChangeComponent() {
        SaveRouteRequest request = new SaveRouteRequest(
                "employee-change", "hr", "入转调离", "/oa/employee-change", "employee-change",
                "PAGE", "EMPLOYEE_CHANGE", 4, true);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsUnregisteredComponent() {
        SaveRouteRequest request = new SaveRouteRequest(
                "unsafe-page", "workspace", "Unsafe", "/oa/unsafe-page", null,
                "PAGE", "ARBITRARY_COMPONENT", 99, true);

        assertThat(validator.validate(request))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("componentKey"));
    }
}
