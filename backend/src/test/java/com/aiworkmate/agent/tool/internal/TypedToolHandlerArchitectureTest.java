package com.aiworkmate.agent.tool.internal;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "com.aiworkmate.agent.tool.internal")
class TypedToolHandlerArchitectureTest {
    @ArchTest
    static final ArchRule every_concrete_handler_uses_a_typed_template = classes()
            .that().resideInAPackage("..agent.tool.internal..")
            .and().haveSimpleNameEndingWith("ToolHandler")
            .and().areNotInterfaces()
            .and().areNotAssignableTo(TypedReadToolHandler.class)
            .should().beAssignableTo(TypedWriteToolHandler.class);
}
