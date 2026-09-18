package com.aiworkmate.agent.tool.adapter;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDomainToolAdapterBoundaryTest {
    private static final Set<Class<?>> ADAPTERS = discover(
            "com.aiworkmate.agent.tool.adapter", "AgentDomainToolAdapter", false);
    private static final Set<Class<?>> PORTS = discover(
            "com.aiworkmate.agent.tool.port", "ToolPort", true);

    @Test
    void eachPortHasOneLocalAdapterAndEachAdapterHasOneBusinessBoundary() {
        ADAPTERS.forEach(adapter -> {
            assertThat(adapter).hasAnnotation(Component.class);
            assertThat(adapter.getSimpleName()).endsWith("AgentDomainToolAdapter");
            assertThat(adapter.getInterfaces())
                    .as(adapter.getSimpleName())
                    .hasSize(1)
                    .allMatch(PORTS::contains);
        });
        PORTS.forEach(port -> assertThat(ADAPTERS.stream().filter(port::isAssignableFrom).toList())
                .as(port.getSimpleName())
                .hasSize(1));
    }

    private static Set<Class<?>> discover(String packageName, String suffix, boolean interfacesOnly) {
        return new ClassFileImporter().importPackages(packageName).stream()
                .filter(javaClass -> javaClass.getSimpleName().endsWith(suffix))
                .filter(javaClass -> !interfacesOnly || javaClass.isInterface())
                .map(JavaClass::reflect)
                .collect(Collectors.toUnmodifiableSet());
    }
}
