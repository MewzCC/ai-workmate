package com.aiworkmate.agent.tool.port;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDomainToolPortContractTest {
    private static final List<Class<?>> PORTS = List.of(
            TodoToolPort.class, LeaveToolPort.class, KnowledgeToolPort.class, NotificationToolPort.class,
            ApprovalConfigurationToolPort.class, ApprovalTaskToolPort.class, HrOrganizationToolPort.class,
            HrEmployeeToolPort.class, EmployeeChangeToolPort.class, AssetToolPort.class);

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
            for (Class<?> contractType : port.getDeclaredClasses()) {
                assertTrue(contractType.isRecord() || contractType.isEnum());
                if (contractType.isRecord()) {
                    for (RecordComponent component : contractType.getRecordComponents()) {
                        assertFalse(component.getGenericType().getTypeName().contains("com.aiworkmate.dto"));
                        assertFalse(component.getGenericType().getTypeName().contains("com.fasterxml.jackson"));
                        assertFalse(component.getGenericType().getTypeName().contains("java.util.Map"));
                    }
                }
            }
        });
    }
}
