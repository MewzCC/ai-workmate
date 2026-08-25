package com.aiworkmate.agent.gateway;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolGatewayContractTest {
    @Test
    void gatewayExposesOnlyStepAndLeaseExecutionContract() {
        List<Method> publicMethods = Arrays.stream(ToolGateway.class.getDeclaredMethods()).toList();

        assertThat(publicMethods).hasSize(1);
        assertThat(publicMethods.get(0).getName()).isEqualTo("execute");
        assertThat(publicMethods.get(0).getParameterTypes()).containsExactly(long.class, WorkerLease.class);
    }

    @Test
    void disabledGatewayMustFailClosed() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        DefaultToolGateway gateway = new DefaultToolGateway(
                properties,
                mock(GatewayExecutionSnapshotMapper.class),
                mock(com.aiworkmate.agent.registry.ToolRegistry.class),
                mock(com.aiworkmate.service.UserAccessService.class),
                new com.aiworkmate.agent.task.AgentHashing(new ObjectMapper()),
                new ToolSchemaValidator(),
                new ToolOutputGuard(),
                new HandlerResolver(List.of()),
                mock(GatewayAuditWriter.class),
                new ObjectMapper()
        );

        ToolGatewayResult result = gateway.execute(
                10L, new WorkerLease("worker-1", 0, "0123456789abcdef0123456789abcdef")
        );

        assertThat(result.decision()).isEqualTo(GatewayDecision.UNAVAILABLE);
        assertThat(result.code()).isEqualTo(GatewayDecisionCode.GATEWAY_DISABLED);
        assertThat(result.output()).isNull();
    }

    @Test
    void workerLeaseStringMustNeverExposeBearerMaterial() {
        String token = "0123456789abcdef0123456789abcdef";

        assertThat(new WorkerLease("worker-1", 0, token).toString())
                .doesNotContain(token)
                .contains("[REDACTED]");
    }

    @Test
    void resolverRejectsDuplicateCodeAndVersion() {
        var first = mock(com.aiworkmate.agent.tool.internal.ToolHandler.class);
        var second = mock(com.aiworkmate.agent.tool.internal.ToolHandler.class);
        when(first.toolCode()).thenReturn("todo.query");
        when(first.handlerVersion()).thenReturn("1.0.0");
        when(second.toolCode()).thenReturn("todo.query");
        when(second.handlerVersion()).thenReturn("1.0.0");

        assertThatThrownBy(() -> new HandlerResolver(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class);
    }
}
