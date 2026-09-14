package com.aiworkmate.agent.capability;

import com.aiworkmate.agent.registry.ToolCode;
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
        Set<String> knownTools = ToolCode.codes();
        Set<String> registered = catalog.all().stream()
                .flatMap(page -> page.tools().stream())
                .map(PageToolReference::toolCode)
                .collect(Collectors.toSet());

        assertThat(registered).isEqualTo(knownTools);
        assertThat(catalog.find("access-control").orElseThrow().writeTools()).isEmpty();
        assertThat(catalog.find("ai-workspace").orElseThrow().writeTools())
                .extracting(PageToolReference::toolCode)
                .containsExactlyInAnyOrder("leave.createDraft", "leave.submit", "leave.apply");
        assertThat(catalog.find("todo").orElseThrow().readTools())
                .extracting(PageToolReference::code)
                .containsExactly(ToolCode.TODO_QUERY);
    }

    @Test
    void mapsLegacyDrawerPageNamesToCanonicalRoutes() {
        assertThat(catalog.canonicalPageId("todo-list")).isEqualTo("todo");
        assertThat(catalog.canonicalPageId("message-center")).isEqualTo("messages");
        assertThat(catalog.find("todo-list")).contains(catalog.find("todo").orElseThrow());
    }

    @Test
    void bindsTheSharedAttendanceReadContractToAllFiveAttendancePages() {
        assertThat(Set.of("attendance-clock", "attendance-exception", "attendance-reissue",
                "attendance-statistics", "attendance-settings")).allSatisfy(pageId ->
                assertThat(catalog.find(pageId).orElseThrow().readTools())
                        .extracting(PageToolReference::code)
                        .containsExactly(ToolCode.ATTENDANCE_QUERY));
    }

    @Test
    void bindsAdministrativeRequestPagesToTheirLeastPrivilegeReadTools() {
        assertThat(catalog.find("visitor-booking").orElseThrow().readTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.VISITOR_QUERY);
        assertThat(catalog.find("seal-usage").orElseThrow().readTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.SEAL_QUERY);
    }
}
