package com.aiworkmate.agent.capability;

import com.aiworkmate.agent.registry.OwnershipPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageCapabilityDefinitionTest {

    @Test
    void createsImmutableManifestAndSeparatesReadFromWriteTools() {
        Set<PageUiCommand> commands = new LinkedHashSet<>(Set.of(PageUiCommand.APPLY_FILTER));
        List<PageToolReference> tools = new ArrayList<>(List.of(
                new PageToolReference("leave.mine", PageToolAccess.READ),
                new PageToolReference("leave.apply", PageToolAccess.SINGLE_WRITE)
        ));
        PageCapabilityDefinition definition = definition(commands, tools, context("status"));

        commands.clear();
        tools.clear();

        assertThat(definition.uiCommands()).containsExactly(PageUiCommand.APPLY_FILTER);
        assertThat(definition.readTools()).extracting(PageToolReference::toolCode).containsExactly("leave.mine");
        assertThat(definition.writeTools()).extracting(PageToolReference::toolCode).containsExactly("leave.apply");
        assertThatThrownBy(() -> definition.tools().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateToolsWhileAllowingMultipleAtomicChoices() {
        assertThatThrownBy(() -> definition(Set.of(), List.of(
                new PageToolReference("leave.mine", PageToolAccess.READ),
                new PageToolReference("leave.mine", PageToolAccess.READ)
        ), PageContextSchema.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");

        PageCapabilityDefinition definition = definition(Set.of(), List.of(
                new PageToolReference("leave.apply", PageToolAccess.SINGLE_WRITE),
                new PageToolReference("leave.submit", PageToolAccess.SINGLE_WRITE)
        ), PageContextSchema.empty());
        assertThat(definition.writeTools()).hasSize(2);
    }

    @Test
    void rejectsIdentityAndInfrastructureFieldsFromUntrustedPageContext() {
        for (String field : Set.of("userId", "tenantId", "role", "permissions", "dataScope", "targetUrl", "filePath")) {
            assertThatThrownBy(() -> new PageContextField(field, PageContextValueType.STRING, 100))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Forbidden");
        }
    }

    @Test
    void rejectsInvalidPageMetadataAndContextLimits() {
        assertThatThrownBy(() -> new PageCapabilityDefinition(
                "Approval_List", "APPROVAL_LIST", 1, Set.of(), List.of(), Set.of("route:approval-list"),
                OwnershipPolicy.SELF, PageContextSchema.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageId");
        assertThatThrownBy(() -> new PageContextSchema(PageContextSchema.PLATFORM_MAX_BYTES + 1, 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBytes");
        assertThatThrownBy(() -> new PageContextSchema(1024, 1, List.of(
                new PageContextField("status", PageContextValueType.STRING, 20),
                new PageContextField("status", PageContextValueType.STRING, 20))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    private PageCapabilityDefinition definition(Set<PageUiCommand> commands,
                                                List<PageToolReference> tools,
                                                PageContextSchema schema) {
        return new PageCapabilityDefinition(
                "my-applications", "MY_APPLICATIONS", 1, commands, tools,
                Set.of("route:my-applications"), OwnershipPolicy.SELF, schema
        );
    }

    private PageContextSchema context(String field) {
        return new PageContextSchema(4096, 2,
                List.of(new PageContextField(field, PageContextValueType.STRING, 100)));
    }
}
