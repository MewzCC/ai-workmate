package com.aiworkmate.agent.capability;

import com.aiworkmate.agent.registry.OwnershipPolicy;
import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.oa.page.OaPage;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.aiworkmate.agent.capability.PageUiCommand.APPLY_FILTER;
import static com.aiworkmate.agent.capability.PageUiCommand.FILL_FORM;
import static com.aiworkmate.agent.capability.PageUiCommand.NAVIGATE;
import static com.aiworkmate.agent.capability.PageUiCommand.OPEN_CREATE_FORM;
import static com.aiworkmate.agent.capability.PageUiCommand.OPEN_DETAIL;
import static com.aiworkmate.agent.capability.PageUiCommand.PREVIEW_SUBMISSION;
import static com.aiworkmate.agent.capability.PageUiCommand.REFRESH_PAGE;
import static com.aiworkmate.agent.registry.ToolCode.*;

/**
 * Single code-owned registry for every enabled OA page. Runtime policy and
 * tenant configuration may remove commands or tools, but cannot add entries.
 */
@Component
public class PageCapabilityCatalog {
    private static final Set<PageUiCommand> LIST_COMMANDS =
            Set.of(NAVIGATE, APPLY_FILTER, OPEN_DETAIL, REFRESH_PAGE);
    private static final Set<PageUiCommand> FORM_COMMANDS =
            Set.of(NAVIGATE, OPEN_CREATE_FORM, FILL_FORM, PREVIEW_SUBMISSION, REFRESH_PAGE);
    private static final Set<PageUiCommand> READ_COMMANDS =
            Set.of(NAVIGATE, OPEN_DETAIL, REFRESH_PAGE);
    private static final Map<String, String> LEGACY_PAGE_ALIASES = Map.of(
            "todo-list", "todo",
            "message-center", "messages"
    );

    private final Map<String, PageCapabilityDefinition> definitions;

    public PageCapabilityCatalog() {
        Map<String, PageCapabilityDefinition> indexed = new LinkedHashMap<>();
        pages().forEach(definition -> {
            if (indexed.putIfAbsent(definition.pageId(), definition) != null) {
                throw new IllegalStateException("Duplicate page capability: " + definition.pageId());
            }
        });
        this.definitions = Map.copyOf(indexed);
    }

    public Optional<PageCapabilityDefinition> find(String pageId) {
        return Optional.ofNullable(definitions.get(canonicalPageId(pageId)));
    }

    public Collection<PageCapabilityDefinition> all() {
        return definitions.values();
    }

    /** Returns real business pages for a tool, excluding the global conversational entry. */
    public Set<String> businessPageIdsForTool(String toolCode) {
        if (toolCode == null) return Set.of();
        return definitions.values().stream()
                .filter(page -> !OaPage.AI_WORKSPACE.routeKey().equals(page.pageId()))
                .filter(page -> page.tools().stream().anyMatch(tool -> toolCode.equals(tool.toolCode())))
                .map(PageCapabilityDefinition::pageId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Returns whether an enabled database PAGE route exactly matches the code-owned page manifest.
     * Legacy aliases are intentionally excluded: they remain accepted only as inbound page context
     * compatibility values and cannot be enabled as new navigation routes.
     */
    public boolean supportsEnabledRoute(String pageId, String componentKey) {
        return definitions.containsKey(pageId) && OaPage.supportsEnabledRoute(pageId, componentKey);
    }

    public String canonicalPageId(String pageId) {
        if (pageId == null) return null;
        return LEGACY_PAGE_ALIASES.getOrDefault(pageId, pageId);
    }

    private List<PageCapabilityDefinition> pages() {
        return List.of(
                page(OaPage.DASHBOARD, OwnershipPolicy.SELF, LIST_COMMANDS,
                        context(text("status"), number("page"), number("size")),
                        tool(TODO_QUERY), tool(NOTIFICATION_MINE), tool(USER_PERMISSION_MINE_QUERY),
                        tool(DASHBOARD_PREFERENCES_UPDATE)),
                page(OaPage.AI_WORKSPACE, OwnershipPolicy.SELF, READ_COMMANDS, PageContextSchema.empty(),
                        allTools()),
                page(OaPage.AI_TASKS, OwnershipPolicy.SELF, LIST_COMMANDS,
                        context(text("status"), text("from"), text("to"), number("page"), number("size")),
                        tool(AGENT_TASK_MINE_QUERY), tool(AGENT_TASK_CANCEL)),
                page(OaPage.TODO, OwnershipPolicy.ASSIGNED_TO_SELF, LIST_COMMANDS,
                        context(text("status"), text("from"), text("to"), number("page"), number("size")),
                        tool(TODO_QUERY)),
                page(OaPage.MESSAGES, OwnershipPolicy.SELF, LIST_COMMANDS,
                        context(number("notificationId"), number("page"), number("size")),
                        tool(NOTIFICATION_MINE), tool(NOTIFICATION_MARK_READ)),
                page(OaPage.LEAVE_APPLICATION, OwnershipPolicy.SELF, FORM_COMMANDS,
                        context(text("applicationId"), text("status"), number("page"), number("size")),
                        tool(LEAVE_MINE), tool(LEAVE_CREATE_DRAFT), tool(LEAVE_UPDATE_DRAFT),
                        tool(LEAVE_SUBMIT), tool(LEAVE_APPLY),
                        tool(LEAVE_WITHDRAW), tool(LEAVE_REMIND)),
                page(OaPage.MY_APPLICATIONS, OwnershipPolicy.SELF, LIST_COMMANDS,
                        context(text("applicationId"), text("status"), number("page"), number("size")),
                        tool(LEAVE_MINE), tool(LEAVE_CREATE_DRAFT), tool(LEAVE_UPDATE_DRAFT),
                        tool(LEAVE_SUBMIT), tool(LEAVE_APPLY),
                        tool(LEAVE_WITHDRAW), tool(LEAVE_REMIND), tool(APPROVAL_APPLICATION_CREATE_DRAFT),
                        tool(APPROVAL_APPLICATION_UPDATE_DRAFT), tool(APPROVAL_APPLICATION_SUBMIT_DRAFT),
                        tool(APPROVAL_APPLICATION_CANCEL_DRAFT), tool(APPROVAL_APPLICATION_WITHDRAW),
                        tool(APPROVAL_APPLICATION_REOPEN), tool(APPROVAL_APPLICATION_REMIND)),

                page(OaPage.APPROVAL_LIST, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        tool(APPROVAL_TASK_QUERY), tool(APPROVAL_TASK_APPROVE), tool(APPROVAL_TASK_REJECT),
                        tool(APPROVAL_TASK_TRANSFER), tool(APPROVAL_TASK_COPY), tool(APPROVAL_TASK_ADD_SIGN)),
                page(OaPage.APPROVAL_START, OwnershipPolicy.SELF, READ_COMMANDS,
                        tool(APPROVAL_CONFIGURATION_QUERY), tool(APPROVAL_APPLICATION_CREATE_DRAFT),
                        tool(APPROVAL_APPLICATION_SUBMIT_DRAFT)),
                page(OaPage.APPROVAL_FORM, OwnershipPolicy.SELF, FORM_COMMANDS,
                        tool(APPROVAL_CONFIGURATION_QUERY), tool(APPROVAL_APPLICATION_CREATE_DRAFT),
                        tool(APPROVAL_APPLICATION_UPDATE_DRAFT), tool(APPROVAL_APPLICATION_SUBMIT_DRAFT),
                        tool(APPROVAL_APPLICATION_CANCEL_DRAFT)),
                page(OaPage.FORM_ENGINE, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        tool(APPROVAL_CONFIGURATION_QUERY)),
                page(OaPage.PROCESS_CONFIG, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        tool(APPROVAL_CONFIGURATION_QUERY)),
                page(OaPage.APPROVAL_RULES, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        tool(APPROVAL_CONFIGURATION_QUERY)),

                page(OaPage.ORG_TREE, OwnershipPolicy.TENANT_SCOPED, READ_COMMANDS,
                        context(text("keyword"), number("limit")), tool(HR_ORGANIZATION_QUERY)),
                page(OaPage.EMPLOYEE_FILES, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(number("employeeId")), tool(HR_ORGANIZATION_QUERY), tool(HR_EMPLOYEE_QUERY)),
                page(OaPage.EMPLOYEE_CHANGE, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("status"), text("changeType"), text("keyword"), number("page"), number("size")),
                        tool(HR_CHANGE_QUERY), tool(HR_CHANGE_APPLY), tool(HR_CHANGE_APPROVE),
                        tool(HR_CHANGE_REJECT), tool(HR_CHANGE_WITHDRAW)),

                page(OaPage.ASSET_LEDGER, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("keyword"), text("category"), text("status"), number("page"), number("size")),
                        tool(ASSET_QUERY), tool(ASSET_CLAIM), tool(ASSET_RETURN), tool(ASSET_REPAIR_START),
                        tool(ASSET_TRANSFER), tool(ASSET_REPAIR_COMPLETE), tool(ASSET_INVENTORY),
                        tool(ASSET_SCRAP)),
                page(OaPage.MEETING_ROOM, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("keyword"), text("roomStatus"), text("from"), text("to"),
                                text("bookingStatus"), number("page"), number("size")),
                        tool(MEETING_QUERY), tool(MEETING_BOOK), tool(MEETING_CANCEL)),
                page(OaPage.VISITOR_BOOKING, OwnershipPolicy.SELF, LIST_COMMANDS,
                        context(number("bookingId"), text("queue"), text("status"), number("page"), number("size")),
                        tool(VISITOR_QUERY), tool(VISITOR_APPLY), tool(VISITOR_CHECK_IN),
                        tool(VISITOR_MARK_ARRIVED), tool(VISITOR_LEAVE), tool(VISITOR_WITHDRAW),
                        tool(VISITOR_NO_SHOW)),
                page(OaPage.SEAL_USAGE, OwnershipPolicy.SELF, LIST_COMMANDS,
                        context(number("usageId"), text("queue"), text("status"), number("page"), number("size")),
                        tool(SEAL_QUERY), tool(SEAL_APPLY), tool(SEAL_REGISTER_USE),
                        tool(SEAL_WITHDRAW), tool(SEAL_RETURN)),

                page(OaPage.EXPENSE, OwnershipPolicy.SELF, FORM_COMMANDS,
                        context(number("applicationId"), text("status"), number("page"), number("size")),
                        tool(EXPENSE_QUERY), tool(EXPENSE_CREATE_DRAFT), tool(EXPENSE_UPDATE_DRAFT),
                        tool(EXPENSE_SUBMIT_DRAFT), tool(EXPENSE_WITHDRAW), tool(EXPENSE_REOPEN)),
                page(OaPage.BUDGET, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(number("budgetId"), text("keyword"), text("status"), number("fiscalYear"),
                                number("page"), number("size")), tool(BUDGET_QUERY), tool(BUDGET_CREATE_DRAFT),
                        tool(BUDGET_UPDATE_DRAFT), tool(BUDGET_ACTIVATE_DRAFT), tool(BUDGET_CANCEL_DRAFT)),
                page(OaPage.CONTRACTS, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(number("contractId"), text("keyword"), text("status"), text("contractType"),
                                text("expiryState"), number("page"), number("size")),
                        tool(CONTRACT_QUERY), tool(CONTRACT_CREATE_DRAFT), tool(CONTRACT_UPDATE_DRAFT),
                        tool(CONTRACT_UPDATE_STATUS), tool(CONTRACT_UPDATE_FULFILLMENT),
                        tool(CONTRACT_RECORD_PAYMENT), tool(CONTRACT_REMIND)),
                page(OaPage.SUPPLIERS, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(number("supplierId"), text("keyword"), text("status"), text("category"),
                                number("page"), number("size")), tool(SUPPLIER_QUERY), tool(SUPPLIER_CREATE_DRAFT),
                        tool(SUPPLIER_UPDATE_DRAFT), tool(SUPPLIER_UPDATE_STATUS)),

                page(OaPage.API_CENTER, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(number("endpointId"), text("keyword"), text("status"), number("page"), number("size")),
                        tool(INTEGRATION_ENDPOINT_QUERY)),
                page(OaPage.PAGE_ACTIONS, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("targetPageId"), text("enabled"), number("page"), number("size")),
                        tool(PAGE_ACTION_QUERY)),
                page(OaPage.RUNTIME_LOGS, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("source"), number("recordId"), text("outcome"), text("keyword"),
                                text("from"), text("to"), number("page"), number("size")), tool(RUNTIME_LOG_QUERY)),
                page(OaPage.PLATFORM_OBSERVABILITY, OwnershipPolicy.TENANT_SCOPED, READ_COMMANDS,
                        context(), tool(RUNTIME_LOG_QUERY), tool(OBSERVABILITY_PREFERENCES_UPDATE),
                        tool(OBSERVABILITY_THRESHOLDS_UPDATE)),
                page(OaPage.SANDBOX_REPLAY, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(number("replayId"), text("keyword"), text("status"), number("page"), number("size")),
                        tool(SANDBOX_REPLAY_QUERY)),

                page(OaPage.ATTENDANCE_CLOCK, OwnershipPolicy.SELF, READ_COMMANDS,
                        context(text("resource")), tool(ATTENDANCE_QUERY), tool(ATTENDANCE_CLOCK)),
                page(OaPage.ATTENDANCE_EXCEPTION, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        attendanceListContext(), tool(ATTENDANCE_QUERY)),
                page(OaPage.ATTENDANCE_REISSUE, OwnershipPolicy.SELF, LIST_COMMANDS,
                        context(text("resource"), text("status"), number("page"), number("size")),
                        tool(ATTENDANCE_QUERY), tool(ATTENDANCE_REISSUE_APPLY),
                        tool(ATTENDANCE_REISSUE_DECIDE)),
                page(OaPage.ATTENDANCE_STATISTICS, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("resource"), number("year"), number("month")), tool(ATTENDANCE_QUERY)),
                page(OaPage.ATTENDANCE_SETTINGS, OwnershipPolicy.TENANT_SCOPED, READ_COMMANDS,
                        context(text("resource")), tool(ATTENDANCE_QUERY), tool(ATTENDANCE_SETTINGS_UPDATE)),

                page(OaPage.ACCESS_CONTROL, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("filterCode")), tool(ACCESS_GOVERNANCE_QUERY)),
                page(OaPage.DATA_PERMISSION, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("scopeType"), text("enabled")), tool(DATA_PERMISSION_QUERY)),
                page(OaPage.AI_PERMISSION, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("toolCode"), text("filterCode"), text("effectiveEnabled")), tool(AI_PERMISSION_QUERY)),
                page(OaPage.KNOWLEDGE_BASE, OwnershipPolicy.FIXED_RESOURCE, LIST_COMMANDS,
                        context(text("query"), number("topK"), number("minScore")), tool(KNOWLEDGE_SEARCH),
                        tool(KNOWLEDGE_BASE_QUERY),
                        tool(KNOWLEDGE_BASE_CREATE), tool(KNOWLEDGE_DOCUMENT_CREATE_TEXT)),
                page(OaPage.AUDIT_CENTER, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("action"), text("resourceType"), text("result"), text("from"), text("to"),
                                number("page"), number("size")), tool(AUDIT_QUERY)),
                page(OaPage.TENANT_CONFIG, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(), tool(TENANT_CONFIGURATION_QUERY)),
                page(OaPage.DICTIONARY, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("keyword"), text("status")), tool(DICTIONARY_QUERY)),
                page(OaPage.SYSTEM_CONFIG, OwnershipPolicy.SELF, READ_COMMANDS,
                        context(), tool(SYSTEM_CAPABILITY_QUERY), tool(USER_SETTINGS_UPDATE))
        );
    }

    private PageCapabilityDefinition page(OaPage page, OwnershipPolicy scope,
                                          Set<PageUiCommand> commands, PageToolReference... tools) {
        return page(page, scope, commands, PageContextSchema.empty(), tools);
    }

    private PageCapabilityDefinition page(OaPage page, OwnershipPolicy scope,
                                          Set<PageUiCommand> commands, PageContextSchema context,
                                          PageToolReference... tools) {
        return new PageCapabilityDefinition(page.routeKey(), page.componentKey(), 1,
                commands, List.of(tools), Set.of("route:" + page.routeKey()), scope, context);
    }

    private PageToolReference tool(ToolCode code) {
        return new PageToolReference(code);
    }

    /**
     * The workspace is the conversational entry for the same fixed tools exposed by all OA pages.
     * Tool Registry, live RBAC, tenant policy and the gateway still narrow this code-owned maximum.
     */
    private PageToolReference[] allTools() {
        return Arrays.stream(ToolCode.values()).map(this::tool).toArray(PageToolReference[]::new);
    }

    private PageContextField text(String name) {
        return new PageContextField(name, PageContextValueType.STRING, 500);
    }

    private PageContextField number(String name) {
        return new PageContextField(name, PageContextValueType.NUMBER, 32);
    }

    private PageContextSchema context(PageContextField... fields) {
        return new PageContextSchema(4096, PageContextSchema.PLATFORM_MAX_DEPTH, List.of(fields));
    }

    private PageContextSchema attendanceListContext() {
        return context(text("resource"), text("from"), text("to"), number("employeeId"),
                number("page"), number("size"));
    }
}
