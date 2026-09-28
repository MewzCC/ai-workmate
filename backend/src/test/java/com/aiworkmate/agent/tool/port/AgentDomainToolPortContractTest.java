package com.aiworkmate.agent.tool.port;

import org.junit.jupiter.api.Test;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDomainToolPortContractTest {
    private static final Set<Class<?>> PORTS = new ClassFileImporter()
            .importPackages("com.aiworkmate.agent.tool.port").stream()
            .filter(JavaClass::isInterface)
            .filter(javaClass -> javaClass.getSimpleName().endsWith("ToolPort"))
            .map(JavaClass::reflect)
            .collect(Collectors.toUnmodifiableSet());

    @Test
    void portsAreFrameworkNeutralInterfacesWithoutGenericExecutionEscapeHatch() {
        PORTS.forEach(port -> {
            assertTrue(port.isInterface());
            assertEquals("com.aiworkmate.agent.tool.port", port.getPackageName());
            for (Method method : port.getDeclaredMethods()) {
                assertTrue(Modifier.isPublic(method.getModifiers()));
                assertEquals(ToolActorContext.class, method.getParameterTypes()[0],
                        () -> port.getSimpleName() + "." + method.getName()
                                + " must receive the gateway-derived context first");
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

    @Test
    void trustedRemoteActorContextCannotCarryClientAssertedAuthorizationOrTargets() {
        assertEquals(
                List.of("tenantId", "userId", "taskId", "stepId", "attempt", "traceId"),
                List.of(ToolActorContext.class.getRecordComponents()).stream()
                        .map(RecordComponent::getName)
                        .toList());
        PORTS.forEach(port -> {
            String contract = port.toGenericString();
            for (Method method : port.getDeclaredMethods()) {
                contract += method.toGenericString();
            }
            assertFalse(contract.contains("java.net.URL"));
            assertFalse(contract.contains("java.net.URI"));
            assertFalse(contract.contains("permissions"));
            assertFalse(contract.contains("roles"));
        });
    }
}
