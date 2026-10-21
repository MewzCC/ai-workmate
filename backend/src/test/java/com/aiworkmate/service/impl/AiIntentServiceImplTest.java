package com.aiworkmate.service.impl;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.planner.PlannerModelClient;
import com.aiworkmate.agent.task.AgentApiRateLimiter;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.config.AiRuntimeProperties;
import com.aiworkmate.dto.AiIntentRequest;
import com.aiworkmate.dto.AiIntentResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiIntentServiceImplTest {
    private final AgentRuntimeProperties runtime = new AgentRuntimeProperties();
    private final AiRuntimeProperties ai = new AiRuntimeProperties();
    private final UserAccessService access = mock(UserAccessService.class);
    private final PlannerModelClient model = mock(PlannerModelClient.class);
    private final AuthenticatedUser user = new AuthenticatedUser(7L, "test", 1L, "EMPLOYEE",
            List.of("EMPLOYEE"), List.of(), List.of("SELF"), 1L);
    private AiIntentServiceImpl service;

    @BeforeEach void setup() {
        runtime.setEnabled(true); runtime.setPlanningEnabled(true);
        ai.setApiKey("unit-test-placeholder");
        when(access.resolveActiveUser(7L)).thenReturn(resolved(1L, List.of("route:ai-workspace")));
        service = new AiIntentServiceImpl(runtime, ai, access, new AgentApiRateLimiter(), model, new ObjectMapper(), Runnable::run);
    }

    @ParameterizedTest @ValueSource(strings = {"CHAT", "OPERATION"})
    void returnsOnlyTheModelClassificationAndPackagesUserTextAsUntrustedJson(String intent) {
        when(model.complete(any(), any())).thenReturn("{\"intent\":\"" + intent + "\"}");
        assertThat(service.classify(new AiIntentRequest("查询会议室和资产"), user).intent().name()).isEqualTo(intent);
        verify(model).complete(contains("Classification does not authorize"), eq("{\"userInput\":\"查询会议室和资产\"}"));
        verifyNoMoreInteractions(model);
    }

    @Test void permissionRevocationAndTenantMismatchRejectBeforeCallingTheModel() {
        when(access.resolveActiveUser(7L)).thenReturn(resolved(1L, List.of()));
        assertThatThrownBy(() -> service.classify(new AiIntentRequest("查询资产"), user))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("PERMISSION_DENIED");
        when(access.resolveActiveUser(7L)).thenReturn(resolved(2L, List.of("route:ai-workspace")));
        assertThatThrownBy(() -> service.classify(new AiIntentRequest("查询资产"), user))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("AUTH_REQUIRED");
        verifyNoInteractions(model);
    }

    @Test void invalidInputAndUnavailableConfigurationNeverFallBackToChat() {
        for (String input : List.of(" ", "x".repeat(4097), "中".repeat(1400))) {
            assertThatThrownBy(() -> service.classify(new AiIntentRequest(input), user))
                    .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("REQUEST_INVALID");
        }
        runtime.setPlanningEnabled(false);
        assertThatThrownBy(() -> service.classify(new AiIntentRequest("你好"), user))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("AI_TASK_CAPABILITY_UNAVAILABLE");
        runtime.setPlanningEnabled(true); ai.setApiKey("development-only-unconfigured");
        assertThatThrownBy(() -> service.classify(new AiIntentRequest("你好"), user))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("AI_TASK_CAPABILITY_UNAVAILABLE");
        verifyNoInteractions(model);
    }

    @Test void malformedModelOutputCannotBecomeAChatFallbackOrAnExecutablePlan() {
        for (String raw : List.of("", "null", "{}", "{\"intent\":null}", "{\"intent\":\"UNKNOWN\"}",
                "{\"intent\":\"chat\"}", "{\"intent\":0}", "{\"intent\":\"CHAT\",\"toolCode\":\"asset.create\"}",
                "{\"intent\":\"CHAT\",\"intent\":\"OPERATION\"}", "[{\"intent\":\"CHAT\"}]", "{\"intent\":\"CHAT\"}{}", "x".repeat(257))) {
            when(model.complete(any(), any())).thenReturn(raw);
            assertThatThrownBy(() -> service.classify(new AiIntentRequest("请处理业务"), user))
                    .as(raw).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("SCHEMA_INVALID");
        }
    }

    @Test void modelFailureIsNotRetriedOrConvertedIntoAClassification() {
        when(model.complete(any(), any())).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> service.classify(new AiIntentRequest("查询资产"), user))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("AI_TASK_CAPABILITY_UNAVAILABLE");
        verify(model, times(1)).complete(any(), any());
    }

    @Test void timeoutCancelsTheBoundedModelCallWithoutReturningAnIntent() {
        var executor = Executors.newSingleThreadExecutor();
        runtime.getLimits().setPlannerTimeoutMs(100);
        when(model.complete(any(), any())).thenAnswer(invocation -> {
            new java.util.concurrent.CountDownLatch(1).await();
            return "{\"intent\":\"CHAT\"}";
        });
        var bounded = new AiIntentServiceImpl(runtime, ai, access, new AgentApiRateLimiter(), model, new ObjectMapper(), executor);
        try {
            assertThatThrownBy(() -> bounded.classify(new AiIntentRequest("查询资产"), user))
                    .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("AI_TASK_CAPABILITY_UNAVAILABLE");
        } finally { executor.shutdownNow(); }
    }

    private ResolvedUserAccess resolved(long tenant, List<String> permissions) {
        return new ResolvedUserAccess(7L, "test", tenant, "EMPLOYEE", List.of("EMPLOYEE"), permissions, List.of("SELF"), 2L);
    }
}
