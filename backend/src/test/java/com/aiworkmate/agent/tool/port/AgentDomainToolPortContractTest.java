package com.aiworkmate.agent.tool.port;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDomainToolPortContractTest {
    private static final List<Class<?>> PORTS = List.of(
            TodoToolPort.class, LeaveToolPort.class, KnowledgeToolPort.class, NotificationToolPort.class);

    @Test
    void portsAreFrameworkNeutralInterfacesWithoutGenericExecutionEscapeHatch() {
        PORTS.forEach(port -> {
            assertTrue(port.isInterface());
            assertEquals("com.aiworkmate.agent.tool.port", port.getPackageName());
            for (Method method : port.getDeclaredMethods()) {
                assertTrue(Modifier.isPublic(method.getModifiers()));
                assertFalse(Set.of("execute", "executeAsAdmin", "testTool").contains(method.getName()));
                assertFalse(method.toGenericString().contains("JsonNode"));
                assertFalse(method.toGenericString().contains("Map<"));
                assertFalse(method.toGenericString().contains("Mapper"));
            }
        });
    }
}
