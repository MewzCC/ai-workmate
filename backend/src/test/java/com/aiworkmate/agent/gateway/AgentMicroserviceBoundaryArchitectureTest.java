package com.aiworkmate.agent.gateway;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class AgentMicroserviceBoundaryArchitectureTest {
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.aiworkmate");

    @Test
    void domainLayersRemainIndependentFromAgentToolContracts() {
        noClasses().that().resideInAnyPackage(
                        "..service..", "..controller..", "..mapper..", "..entity..", "..dto..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..agent.tool.port..", "..agent.tool.adapter..", "..agent.tool.internal..")
                .check(classes);
    }

    @Test
    void onlyAdaptersMayKnowConcreteToolAdapters() {
        noClasses().that().resideOutsideOfPackage("..agent.tool.adapter..")
                .should().dependOnClassesThat().resideInAPackage("..agent.tool.adapter..")
                .check(classes);
    }

    @Test
    void portsUseOnlyTransportNeutralJavaContracts() {
        classes().that().resideInAPackage("..agent.tool.port..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "java..", "..agent.tool.port..")
                .check(classes);
    }

    @Test
    void localAdaptersDependOnlyOnPortsAndCurrentDomainFacades() {
        classes().that().resideInAPackage("..agent.tool.adapter..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "java..",
                        "..agent.tool.adapter..",
                        "..agent.tool.port..",
                        "..service..",
                        "..dto..",
                        "..common..",
                        "lombok..",
                        "org.springframework.stereotype..")
                .check(classes);
    }
}
