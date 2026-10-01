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
    void registersAllEnabledPagesWithUniqueComponents() {
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
        assertThat(catalog.find("ai-workspace").orElseThrow().tools())
                .extracting(PageToolReference::toolCode)
                .containsExactlyInAnyOrderElementsOf(knownTools);
        assertThat(catalog.find("todo").orElseThrow().readTools())
                .extracting(PageToolReference::code)
                .containsExactly(ToolCode.TODO_QUERY);
        assertThat(catalog.find("dashboard").orElseThrow().readTools())
                .extracting(PageToolReference::code)
                .contains(ToolCode.USER_PERMISSION_MINE_QUERY);
        assertThat(catalog.find("dashboard").orElseThrow().writeTools())
                .extracting(PageToolReference::code)
                .containsExactly(ToolCode.DASHBOARD_PREFERENCES_UPDATE);
        assertThat(catalog.find("ai-workspace").orElseThrow().readTools())
                .extracting(PageToolReference::code)
                .contains(ToolCode.USER_PERMISSION_MINE_QUERY);
        assertThat(catalog.find("knowledge-base").orElseThrow().writeTools())
                .extracting(PageToolReference::code)
                .containsExactly(ToolCode.KNOWLEDGE_BASE_CREATE, ToolCode.KNOWLEDGE_BASE_UPDATE,
                        ToolCode.KNOWLEDGE_DOCUMENT_CREATE_TEXT, ToolCode.KNOWLEDGE_DOCUMENT_REINDEX);
        assertThat(catalog.find("knowledge-base").orElseThrow().readTools())
                .extracting(PageToolReference::code)
                .containsExactly(ToolCode.KNOWLEDGE_SEARCH, ToolCode.KNOWLEDGE_BASE_QUERY,
                        ToolCode.KNOWLEDGE_DOCUMENT_QUERY);
        assertThat(catalog.find("form-engine").orElseThrow().writeTools())
                .extracting(PageToolReference::code)
                .containsExactly(ToolCode.APPROVAL_FORM_CREATE_DRAFT, ToolCode.APPROVAL_FORM_UPDATE_DRAFT,
                        ToolCode.APPROVAL_FORM_PUBLISH_DRAFT);
        assertThat(catalog.find("process-config").orElseThrow().writeTools())
                .extracting(PageToolReference::code)
                .containsExactly(ToolCode.APPROVAL_PROCESS_CREATE_DRAFT,
                        ToolCode.APPROVAL_PROCESS_UPDATE_DRAFT,
                        ToolCode.APPROVAL_PROCESS_PUBLISH_DRAFT);
        assertThat(catalog.find("approval-rules").orElseThrow().writeTools())
                .extracting(PageToolReference::code)
                .containsExactly(ToolCode.APPROVAL_RULE_CREATE_DRAFT,
                        ToolCode.APPROVAL_RULE_UPDATE_DRAFT,
                        ToolCode.APPROVAL_RULE_ENABLE_DRAFT);
    }

    @Test
    void everyProductionPageHasAtLeastOneControlledTool() {
        assertThat(catalog.all()).allSatisfy(page ->
                assertThat(page.tools()).as(page.pageId()).isNotEmpty());
        assertThat(catalog.find("ai-tasks").orElseThrow().readTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.AGENT_TASK_MINE_QUERY);
        assertThat(catalog.find("ai-tasks").orElseThrow().writeTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.AGENT_TASK_CANCEL);
        assertThat(catalog.find("leave-application").orElseThrow().tools())
                .extracting(PageToolReference::code)
                .containsExactlyInAnyOrder(ToolCode.LEAVE_MINE, ToolCode.LEAVE_CREATE_DRAFT,
                        ToolCode.LEAVE_UPDATE_DRAFT, ToolCode.LEAVE_SUBMIT, ToolCode.LEAVE_APPLY, ToolCode.LEAVE_WITHDRAW,
                        ToolCode.LEAVE_REMIND);
    }

    @Test
    void everyWorkspaceToolHasAtLeastOnePermissionBearingBusinessPage() {
        assertThat(catalog.find("ai-workspace").orElseThrow().tools()).allSatisfy(tool ->
                assertThat(catalog.businessPageIdsForTool(tool.toolCode())).as(tool.toolCode()).isNotEmpty());
        assertThat(catalog.businessPageIdsForTool("meeting.book")).containsExactly("meeting-room");
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
                .containsExactly(ToolCode.ASSET_CLAIM, ToolCode.ASSET_RETURN, ToolCode.ASSET_REPAIR_START,
                        ToolCode.ASSET_TRANSFER, ToolCode.ASSET_REPAIR_COMPLETE, ToolCode.ASSET_INVENTORY,
                        ToolCode.ASSET_SCRAP);
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
                        ToolCode.APPROVAL_APPLICATION_REOPEN,
                        ToolCode.APPROVAL_APPLICATION_UPDATE_DRAFT,
                        ToolCode.APPROVAL_APPLICATION_CANCEL_DRAFT,
                        ToolCode.APPROVAL_APPLICATION_REMIND);
        assertThat(catalog.find("approval-start").orElseThrow().writeTools())
                .extracting(PageToolReference::code)
                .doesNotContain(ToolCode.APPROVAL_APPLICATION_WITHDRAW,
                        ToolCode.APPROVAL_APPLICATION_REOPEN);
    }

    @Test
    void approvalCenterOffersReadAndSingleTaskActionsOnly() {
        var page = catalog.find("approval-list").orElseThrow();
        assertThat(page.readTools()).extracting(PageToolReference::code)
                .containsExactly(ToolCode.APPROVAL_TASK_QUERY);
        assertThat(page.writeTools()).extracting(PageToolReference::code)
                .containsExactly(ToolCode.APPROVAL_TASK_APPROVE, ToolCode.APPROVAL_TASK_REJECT,
                        ToolCode.APPROVAL_TASK_TRANSFER, ToolCode.APPROVAL_TASK_COPY,
                        ToolCode.APPROVAL_TASK_ADD_SIGN);
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
                .containsExactly(ToolCode.ATTENDANCE_REISSUE_APPLY,
                        ToolCode.ATTENDANCE_REISSUE_DECIDE);
        assertThat(catalog.find("attendance-clock").orElseThrow().writeTools())
                .extracting(PageToolReference::code)
                .containsExactly(ToolCode.ATTENDANCE_CLOCK);
        assertThat(catalog.find("attendance-settings").orElseThrow().writeTools())
                .extracting(PageToolReference::code)
                .containsExactly(ToolCode.ATTENDANCE_SETTINGS_UPDATE);
    }

    @Test
    void bindsAdministrativeRequestPagesToTheirLeastPrivilegeReadTools() {
        var visitor = catalog.find("visitor-booking").orElseThrow();
        assertThat(visitor.readTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.VISITOR_QUERY);
        assertThat(visitor.writeTools())
                .extracting(PageToolReference::code)
                .containsExactly(ToolCode.VISITOR_APPLY, ToolCode.VISITOR_CHECK_IN,
                        ToolCode.VISITOR_MARK_ARRIVED, ToolCode.VISITOR_LEAVE,
                        ToolCode.VISITOR_WITHDRAW, ToolCode.VISITOR_NO_SHOW);
        assertThat(catalog.find("seal-usage").orElseThrow().readTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.SEAL_QUERY);
        assertThat(catalog.find("seal-usage").orElseThrow().writeTools())
                .extracting(PageToolReference::code)
                .containsExactly(ToolCode.SEAL_APPLY, ToolCode.SEAL_REGISTER_USE,
                        ToolCode.SEAL_WITHDRAW, ToolCode.SEAL_RETURN);
    }

    @Test
    void bindsEachFinancePageToItsLeastPrivilegeTools() {
        var expense = catalog.find("expense").orElseThrow();
        assertThat(expense.readTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.EXPENSE_QUERY);
        assertThat(expense.writeTools())
                .extracting(PageToolReference::code).containsExactly(
                        ToolCode.EXPENSE_CREATE_DRAFT, ToolCode.EXPENSE_UPDATE_DRAFT,
                        ToolCode.EXPENSE_SUBMIT_DRAFT, ToolCode.EXPENSE_WITHDRAW,
                        ToolCode.EXPENSE_REOPEN);
        var budget = catalog.find("budget").orElseThrow();
        assertThat(budget.readTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.BUDGET_QUERY);
        assertThat(budget.writeTools())
                .extracting(PageToolReference::code).containsExactly(
                        ToolCode.BUDGET_CREATE_DRAFT, ToolCode.BUDGET_UPDATE_DRAFT,
                        ToolCode.BUDGET_ACTIVATE_DRAFT, ToolCode.BUDGET_CANCEL_DRAFT);
        assertThat(catalog.find("contracts").orElseThrow().readTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.CONTRACT_QUERY);
        assertThat(catalog.find("contracts").orElseThrow().writeTools())
                .extracting(PageToolReference::code).containsExactly(
                        ToolCode.CONTRACT_CREATE_DRAFT, ToolCode.CONTRACT_UPDATE_DRAFT,
                        ToolCode.CONTRACT_UPDATE_STATUS, ToolCode.CONTRACT_UPDATE_FULFILLMENT,
                        ToolCode.CONTRACT_RECORD_PAYMENT, ToolCode.CONTRACT_REMIND);
        assertThat(catalog.find("suppliers").orElseThrow().readTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.SUPPLIER_QUERY);
        assertThat(catalog.find("suppliers").orElseThrow().writeTools())
                .extracting(PageToolReference::code).containsExactly(
                        ToolCode.SUPPLIER_CREATE_DRAFT, ToolCode.SUPPLIER_UPDATE_DRAFT,
                        ToolCode.SUPPLIER_UPDATE_STATUS);
    }

    @Test
    void employeeChangePageOffersReadAndApplicationOnlyWrite() {
        var page = catalog.find("employee-change").orElseThrow();
        assertThat(page.readTools()).extracting(PageToolReference::code)
                .containsExactly(ToolCode.HR_CHANGE_QUERY);
        assertThat(page.writeTools()).extracting(PageToolReference::code)
                .containsExactly(ToolCode.HR_CHANGE_APPLY, ToolCode.HR_CHANGE_APPROVE,
                        ToolCode.HR_CHANGE_REJECT, ToolCode.HR_CHANGE_WITHDRAW);
    }

    @Test
    void bindsEachPlatformOperationsPageToItsLeastPrivilegeReadTool() {
        assertThat(catalog.find("api-center").orElseThrow().readTools()).extracting(PageToolReference::code).containsExactly(ToolCode.INTEGRATION_ENDPOINT_QUERY);
        assertThat(catalog.find("page-actions").orElseThrow().readTools()).extracting(PageToolReference::code).containsExactly(ToolCode.PAGE_ACTION_QUERY);
        assertThat(catalog.find("runtime-logs").orElseThrow().readTools()).extracting(PageToolReference::code).containsExactly(ToolCode.RUNTIME_LOG_QUERY);
        assertThat(catalog.find("sandbox-replay").orElseThrow().readTools()).extracting(PageToolReference::code).containsExactly(ToolCode.SANDBOX_REPLAY_QUERY);
        assertThat(catalog.find("platform-observability").orElseThrow().readTools())
                .extracting(PageToolReference::code).containsExactly(ToolCode.RUNTIME_LOG_QUERY);
        assertThat(catalog.find("platform-observability").orElseThrow().writeTools())
                .extracting(PageToolReference::code).containsExactly(
                        ToolCode.OBSERVABILITY_PREFERENCES_UPDATE, ToolCode.OBSERVABILITY_THRESHOLDS_UPDATE);
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
        assertThat(catalog.find("dictionary").orElseThrow().writeTools()).extracting(PageToolReference::code)
                .containsExactly(ToolCode.DICTIONARY_TYPE_CREATE, ToolCode.DICTIONARY_TYPE_UPDATE);
        assertThat(catalog.find("system-config").orElseThrow().readTools()).extracting(PageToolReference::code).containsExactly(ToolCode.SYSTEM_CAPABILITY_QUERY);
        assertThat(catalog.find("system-config").orElseThrow().writeTools()).extracting(PageToolReference::code).containsExactly(ToolCode.USER_SETTINGS_UPDATE);
    }
}
