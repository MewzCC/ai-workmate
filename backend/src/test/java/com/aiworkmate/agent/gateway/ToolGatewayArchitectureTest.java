package com.aiworkmate.agent.gateway;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ToolGatewayArchitectureTest {
    private final JavaClasses classes = new ClassFileImporter().importPackages("com.aiworkmate");

    @Test
    void onlyGatewayMayDependOnInternalToolHandlers() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages("..agent.gateway..", "..agent.tool.internal..")
                .should().dependOnClassesThat().resideInAPackage("..agent.tool.internal..");

        rule.check(classes);
    }

    @Test
    void handlersMustNotDependOnInfrastructureEscapeHatches() {
        noClasses().that().resideInAPackage("..agent.tool.internal..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..agent.tool.adapter..",
                        "..service..",
                        "..dto..",
                        "..mapper..",
                        "com.baomidou.mybatisplus.core.mapper..",
                        "..controller..",
                        "java.io..",
                        "java.nio.file..",
                        "java.net..",
                        "org.springframework.jdbc..",
                        "org.springframework.web.client..",
                        "org.springframework.web.reactive.function.client.."
                ).check(classes);

        noClasses().that().resideInAPackage("..agent.tool.internal..")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.lang.ProcessBuilder")
                .check(classes);

        noClasses().that().resideInAPackage("..agent.tool.internal..")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.lang.Runtime")
                .check(classes);
    }

    @Test
    void domainToolPortsMustRemainFrameworkAndInfrastructureNeutral() {
        noClasses().that().resideInAPackage("..agent.tool.port..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..agent.gateway..",
                        "..agent.tool.adapter..",
                        "..agent.tool.internal..",
                        "..controller..",
                        "..dto..",
                        "..mapper..",
                        "..service..",
                        "com.fasterxml.jackson..",
                        "org.springframework.."
                ).check(classes);
    }

    @Test
    void localAdaptersMustNotBypassGatewayOrDependOnWebAndPersistenceLayers() {
        noClasses().that().resideInAPackage("..agent.tool.adapter..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..agent.gateway..",
                        "..agent.tool.internal..",
                        "..controller..",
                        "..mapper..",
                        "com.baomidou.mybatisplus..",
                        "org.springframework.jdbc..",
                        "org.springframework.web.client..",
                        "org.springframework.web.reactive.function.client.."
                ).check(classes);
    }
}
