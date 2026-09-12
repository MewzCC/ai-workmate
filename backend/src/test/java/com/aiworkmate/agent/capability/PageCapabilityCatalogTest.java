package com.aiworkmate.agent.capability;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PageCapabilityCatalogTest {
    private final PageCapabilityCatalog catalog = new PageCapabilityCatalog();

    @Test
    void registersAllFortyOneEnabledPagesWithUniqueComponents() {
        assertThat(catalog.all()).hasSize(41);
        assertThat(catalog.all()).extracting(PageCapabilityDefinition::pageId).doesNotHaveDuplicates();
        assertThat(catalog.all()).extracting(PageCapabilityDefinition::componentKey).doesNotHaveDuplicates();
        assertThat(catalog.all()).allSatisfy(page ->
                assertThat(page.requiredPermissions()).containsExactly("route:" + page.pageId()));
    }

    @Test
    void exposesOnlyExistingPhaseTwoToolsAndKeepsWritesAtomic() {
        Set<String> knownTools = Set.of(
                "todo.query", "leave.mine", "knowledge.search", "notification.mine",
                "leave.createDraft", "leave.submit", "leave.apply", "approval.configuration.query",
                "approval.task.query"
        );
        Set<String> registered = catalog.all().stream()
                .flatMap(page -> page.tools().stream())
                .map(PageToolReference::toolCode)
                .collect(Collectors.toSet());

        assertThat(registered).isEqualTo(knownTools);
        assertThat(catalog.find("access-control").orElseThrow().writeTools()).isEmpty();
        assertThat(catalog.find("ai-workspace").orElseThrow().writeTools())
                .extracting(PageToolReference::toolCode)
                .containsExactlyInAnyOrder("leave.createDraft", "leave.submit", "leave.apply");
    }

    @Test
    void mapsLegacyDrawerPageNamesToCanonicalRoutes() {
        assertThat(catalog.canonicalPageId("todo-list")).isEqualTo("todo");
        assertThat(catalog.canonicalPageId("message-center")).isEqualTo("messages");
        assertThat(catalog.find("todo-list")).contains(catalog.find("todo").orElseThrow());
    }
}
