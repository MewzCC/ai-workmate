package com.aiworkmate.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class IsolatedAgentResourceListenerTest {
    @Test void ownsResourcesBeforeBootstrapAndCleansWithoutReloadingAClosedContext() {
        var environment = mock(AgentIntegrationEnvironment.class);
        var owner = new AbstractIsolatedAgentIntegrationTest() {
            @Override protected AgentIntegrationEnvironment environment() { return environment; }
        };
        var context = mock(TestContext.class);
        var listener = new AbstractIsolatedAgentIntegrationTest.ResourceListener();
        String key = listener.getClass().getName() + ".environment";
        when(context.getTestInstance()).thenReturn(owner);
        when(context.getApplicationContext()).thenThrow(new IllegalStateException("bootstrap failed"));
        assertThatThrownBy(() -> listener.prepareTestInstance(context)).isInstanceOf(IllegalStateException.class);
        var order = inOrder(context);
        order.verify(context).setAttribute(key, environment);
        order.verify(context).getApplicationContext();

        clearInvocations(context);
        when(context.removeAttribute(key)).thenReturn(environment);
        listener.afterTestClass(context);
        verify(environment).close();
        verify(context, never()).getTestInstance();
        verify(context, never()).getApplicationContext();
    }

    @Test void ordersCleanupAfterSpringClosesConsumersAndBeforeJvmExit() {
        var listener = new AbstractIsolatedAgentIntegrationTest.ResourceListener();
        assertThat(listener.getOrder()).isLessThan(new DependencyInjectionTestExecutionListener().getOrder())
                .isLessThan(new DirtiesContextTestExecutionListener().getOrder());
        listener.afterTestClass(mock(TestContext.class));
    }
}
