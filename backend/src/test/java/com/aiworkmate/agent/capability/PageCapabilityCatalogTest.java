package com.aiworkmate.agent.capability;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.oa.page.OaPage;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PageCapabilityCatalogTest {
    private final PageCapabilityCatalog catalog = new PageCapabilityCatalog();

    @Test
    void registersAllFortyOneEnabledPagesWithUniqueComponents() {
        assertThat(catalog.all()).hasSize(OaPage.values().length);
        assertThat(catalog.all()).extracting(PageCapabilityDefinition::pageId).doesNotHaveDuplicates();
        assertThat(catalog.all()).extracting(PageCapabilityDefinition::componentKey).doesNotHaveDuplicates();
        assertThat(catalog.all()).extracting(PageCapabilityDefinition::pageId)
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(OaPage.values()).map(OaPage::routeKey).toList());
        assertThat(catalog.all()).extracting(PageCapabilityDefinition::componentKey)
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(OaPage.values()).map(OaPage::componentKey).toList());
        assertThat(catalog.all()).allSatisfy(page ->
                assertThat(page.requiredPermissions()).containsExactly("route:" + page.pageId()));
    }

    @Test
    void isTheOnlyBackendAuthorityForEnabledRouteAndComponentPairs() {
        assertThat(catalog.supportsEnabledRoute("asset-ledger", "ASSET_LEDGER")).isTrue();
        assertThat(catalog.supportsEnabledRoute("asset-ledger", "MEETING_ROOM")).isFalse();
        assertThat(catalog.supportsEnabledRoute("unknown-page", "ASSET_LEDGER")).isFalse();
        assertThat(catalog.supportsEnabledRoute("todo-list", "TODO_LIST")).isFalse();
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
                .containsExactlyInAnyOrder("notification.markRead", "leave.createDraft", "leave.submit",
                        "leave.apply", "leave.withdraw", "attendance.reissue.apply",
                        "approval.application.createDraft", "approval.application.submitDraft",
                        "approval.application.withdraw", "approval.application.reopen");
        assertThat(catalog.find("todo").orElseThrow().readTools())
                .extracting(PageToolReference::code)
                .containsExactly(ToolCode.TODO_QUERY);
    }

    @Test
    void everyProductionPageHasAtLeastOneControlledTool() {
        assertThat(catalog.all()).allSatisfy(page ->
                assertThat(page.tools()).as(page.pageId()).isNotEmpty());
        assertThat(catalog.find("ai-tasks").orElseThrow().readTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.AGENT_TASK_MINE_QUERY);
        assertThat(catalog.find("leave-application").orElseThrow().tools())
                .extracting(PageToolReference::code)
                .containsExactlyInAnyOrder(ToolCode.LEAVE_MINE, ToolCode.LEAVE_CREATE_DRAFT,
                        ToolCode.LEAVE_SUBMIT, ToolCode.LEAVE_APPLY, ToolCode.LEAVE_WITHDRAW);
    }

    @Test
    void meetingPageOffersReadAndConfirmedAtomicWrites() {
        var page = catalog.find("meeting-room").orElseThrow();
        assertThat(page.readTools()).extracting(PageToolReference::code)
                .containsExactly(ToolCode.MEETING_QUERY);
        assertThat(page.writeTools()).extracting(PageToolReference::code)
                .containsExactly(ToolCode.MEETING_BOOK, ToolCode.MEETING_CANCEL);
    }

    @Test
    void assetPageOffersReadAndAtomicLifecycleWrites() {
        var page = catalog.find("asset-ledger").orElseThrow();
        assertThat(page.readTools()).extracting(PageToolReference::code)
                .containsExactly(ToolCode.ASSET_QUERY);
        assertThat(page.writeTools()).extracting(PageToolReference::code)
                .containsExactly(ToolCode.ASSET_CLAIM, ToolCode.ASSET_RETURN, ToolCode.ASSET_REPAIR_START);
    }

    @Test
    void genericApprovalEntryPagesShareAtomicDraftLifecycleTools() {
        assertThat(Set.of("approval-start", "approval-form", "my-applications")).allSatisfy(pageId ->
                assertThat(catalog.find(pageId).orElseThrow().writeTools())
                        .extracting(PageToolReference::code)
                        .contains(ToolCode.APPROVAL_APPLICATION_CREATE_DRAFT,
                                ToolCode.APPROVAL_APPLICATION_SUBMIT_DRAFT));
        assertThat(catalog.find("my-applications").orElseThrow().writeTools())
                .extracting(PageToolReference::code)
                .contains(ToolCode.APPROVAL_APPLICATION_WITHDRAW,
                        ToolCode.APPROVAL_APPLICATION_REOPEN);
        assertThat(catalog.find("approval-start").orElseThrow().writeTools())
                .extracting(PageToolReference::code)
                .doesNotContain(ToolCode.APPROVAL_APPLICATION_WITHDRAW,
                        ToolCode.APPROVAL_APPLICATION_REOPEN);
    }

    @Test
    void messagePageOffersSelfOwnedReadAndSingleItemWrite() {
        var page = catalog.find("messages").orElseThrow();
        assertThat(page.readTools()).extracting(PageToolReference::code)
                .containsExactly(ToolCode.NOTIFICATION_MINE);
        assertThat(page.writeTools()).extracting(PageToolReference::code)
                .containsExactly(ToolCode.NOTIFICATION_MARK_READ);
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
        assertThat(catalog.find("attendance-reissue").orElseThrow().writeTools())
                .extracting(PageToolReference::code)
                .containsExactly(ToolCode.ATTENDANCE_REISSUE_APPLY);
    }

    @Test
    void bindsAdministrativeRequestPagesToTheirLeastPrivilegeReadTools() {
        assertThat(catalog.find("visitor-booking").orElseThrow().readTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.VISITOR_QUERY);
        assertThat(catalog.find("seal-usage").orElseThrow().readTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.SEAL_QUERY);
    }

    @Test
    void bindsEachFinancePageToItsLeastPrivilegeReadTool() {
        assertThat(catalog.find("expense").orElseThrow().readTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.EXPENSE_QUERY);
        assertThat(catalog.find("budget").orElseThrow().readTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.BUDGET_QUERY);
        assertThat(catalog.find("contracts").orElseThrow().readTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.CONTRACT_QUERY);
        assertThat(catalog.find("suppliers").orElseThrow().readTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.SUPPLIER_QUERY);
    }

    @Test
    void bindsEachPlatformOperationsPageToItsLeastPrivilegeReadTool() {
        assertThat(catalog.find("api-center").orElseThrow().readTools()).extracting(PageToolReference::code).containsExactly(ToolCode.INTEGRATION_ENDPOINT_QUERY);
        assertThat(catalog.find("page-actions").orElseThrow().readTools()).extracting(PageToolReference::code).containsExactly(ToolCode.PAGE_ACTION_QUERY);
        assertThat(catalog.find("runtime-logs").orElseThrow().readTools()).extracting(PageToolReference::code).containsExactly(ToolCode.RUNTIME_LOG_QUERY);
        assertThat(catalog.find("sandbox-replay").orElseThrow().readTools()).extracting(PageToolReference::code).containsExactly(ToolCode.SANDBOX_REPLAY_QUERY);
    }

    @Test
    void bindsSecurityGovernancePagesWithoutGenericAdministrationTools() {
        assertThat(catalog.find("access-control").orElseThrow().readTools()).extracting(PageToolReference::code).containsExactly(ToolCode.ACCESS_GOVERNANCE_QUERY);
        assertThat(catalog.find("data-permission").orElseThrow().readTools()).extracting(PageToolReference::code).containsExactly(ToolCode.DATA_PERMISSION_QUERY);
        assertThat(catalog.find("ai-permission").orElseThrow().readTools()).extracting(PageToolReference::code).containsExactly(ToolCode.AI_PERMISSION_QUERY);
    }

    @Test
    void bindsOperationalGovernancePagesToNarrowReadTools() {
        assertThat(catalog.find("audit-center").orElseThrow().readTools()).extracting(PageToolReference::code).containsExactly(ToolCode.AUDIT_QUERY);
        assertThat(catalog.find("tenant-config").orElseThrow().readTools()).extracting(PageToolReference::code).containsExactly(ToolCode.TENANT_CONFIGURATION_QUERY);
        assertThat(catalog.find("dictionary").orElseThrow().readTools()).extracting(PageToolReference::code).containsExactly(ToolCode.DICTIONARY_QUERY);
        assertThat(catalog.find("system-config").orElseThrow().readTools()).extracting(PageToolReference::code).containsExactly(ToolCode.SYSTEM_CAPABILITY_QUERY);
    }
}
