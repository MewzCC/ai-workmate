package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ConfirmationPolicy;
import com.aiworkmate.agent.registry.RetryPolicy;
import com.aiworkmate.agent.registry.RiskLevel;
import com.aiworkmate.agent.registry.ToolWriteProfile;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.aiworkmate.agent.tool.internal")
class TypedToolHandlerArchitectureTest {
    @ArchTest
    static final ArchRule every_concrete_handler_uses_a_typed_template = classes()
            .that().resideInAPackage("..agent.tool.internal..")
            .and().haveSimpleNameEndingWith("ToolHandler")
            .and().areNotInterfaces()
            .and().areNotAssignableTo(TypedReadToolHandler.class)
            .should().beAssignableTo(TypedWriteToolHandler.class);

    @ArchTest
    static final ArchRule concrete_handlers_cannot_generate_operation_keys_directly = noClasses()
            .that().haveSimpleNameEndingWith("ToolHandler")
            .and().doNotHaveSimpleName("TypedOperationKeyWriteToolHandler")
            .should().dependOnClassesThat().areAssignableTo(StableToolOperationKey.class);

    @ArchTest
    static final ArchRule concrete_handlers_cannot_assemble_risk_policy = noClasses()
            .that().resideInAPackage("..agent.tool.internal..")
            .and().haveSimpleNameEndingWith("ToolHandler")
            .and().areNotInterfaces()
            .and().doNotHaveSimpleName("TypedReadToolHandler")
            .and().doNotHaveSimpleName("TypedWriteToolHandler")
            .and().doNotHaveSimpleName("TypedOperationKeyWriteToolHandler")
            .and().doNotHaveSimpleName("TypedVersionedWriteToolHandler")
            .and().doNotHaveSimpleName("TypedNaturallyIdempotentWriteToolHandler")
            .should().dependOnClassesThat().belongToAnyOf(
                    RiskLevel.class,
                    RetryPolicy.class,
                    ConfirmationPolicy.class,
                    ToolWriteProfile.class);
}
