package com.aiworkmate.agent.capability;

import com.aiworkmate.agent.registry.OwnershipPolicy;
import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.oa.page.OaPage;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.aiworkmate.agent.capability.PageToolAccess.READ;
import static com.aiworkmate.agent.capability.PageToolAccess.SINGLE_WRITE;
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
                        read(TODO_QUERY), read(NOTIFICATION_MINE)),
                page(OaPage.AI_WORKSPACE, OwnershipPolicy.SELF, READ_COMMANDS, PageContextSchema.empty(),
                        read(TODO_QUERY), read(LEAVE_MINE), read(KNOWLEDGE_SEARCH), read(NOTIFICATION_MINE),
                        write(NOTIFICATION_MARK_READ), write(LEAVE_CREATE_DRAFT), write(LEAVE_SUBMIT),
                        write(LEAVE_APPLY), write(LEAVE_WITHDRAW), write(ATTENDANCE_REISSUE_APPLY),
                        write(APPROVAL_APPLICATION_CREATE_DRAFT), write(APPROVAL_APPLICATION_SUBMIT_DRAFT),
                        write(APPROVAL_APPLICATION_WITHDRAW), write(APPROVAL_APPLICATION_REOPEN)),
                page(OaPage.AI_TASKS, OwnershipPolicy.SELF, LIST_COMMANDS,
                        context(text("status"), text("from"), text("to"), number("page"), number("size")),
                        read(AGENT_TASK_MINE_QUERY)),
                page(OaPage.TODO, OwnershipPolicy.ASSIGNED_TO_SELF, LIST_COMMANDS,
                        context(text("status"), text("from"), text("to"), number("page"), number("size")),
                        read(TODO_QUERY)),
                page(OaPage.MESSAGES, OwnershipPolicy.SELF, LIST_COMMANDS,
                        context(number("notificationId"), number("page"), number("size")),
                        read(NOTIFICATION_MINE), write(NOTIFICATION_MARK_READ)),
                page(OaPage.LEAVE_APPLICATION, OwnershipPolicy.SELF, FORM_COMMANDS,
                        context(text("applicationId"), text("status"), number("page"), number("size")),
                        read(LEAVE_MINE), write(LEAVE_CREATE_DRAFT), write(LEAVE_SUBMIT), write(LEAVE_APPLY), write(LEAVE_WITHDRAW)),
                page(OaPage.MY_APPLICATIONS, OwnershipPolicy.SELF, LIST_COMMANDS,
                        context(text("applicationId"), text("status"), number("page"), number("size")),
                        read(LEAVE_MINE), write(LEAVE_CREATE_DRAFT), write(LEAVE_SUBMIT), write(LEAVE_APPLY),
                        write(LEAVE_WITHDRAW), write(APPROVAL_APPLICATION_CREATE_DRAFT),
                        write(APPROVAL_APPLICATION_SUBMIT_DRAFT), write(APPROVAL_APPLICATION_WITHDRAW),
                        write(APPROVAL_APPLICATION_REOPEN)),

                page(OaPage.APPROVAL_LIST, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        read(APPROVAL_TASK_QUERY)),
                page(OaPage.APPROVAL_START, OwnershipPolicy.SELF, READ_COMMANDS,
                        read(APPROVAL_CONFIGURATION_QUERY), write(APPROVAL_APPLICATION_CREATE_DRAFT),
                        write(APPROVAL_APPLICATION_SUBMIT_DRAFT)),
                page(OaPage.APPROVAL_FORM, OwnershipPolicy.SELF, FORM_COMMANDS,
                        read(APPROVAL_CONFIGURATION_QUERY), write(APPROVAL_APPLICATION_CREATE_DRAFT),
                        write(APPROVAL_APPLICATION_SUBMIT_DRAFT)),
                page(OaPage.FORM_ENGINE, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        read(APPROVAL_CONFIGURATION_QUERY)),
                page(OaPage.PROCESS_CONFIG, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        read(APPROVAL_CONFIGURATION_QUERY)),
                page(OaPage.APPROVAL_RULES, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        read(APPROVAL_CONFIGURATION_QUERY)),

                page(OaPage.ORG_TREE, OwnershipPolicy.TENANT_SCOPED, READ_COMMANDS,
                        context(text("keyword"), number("limit")), read(HR_ORGANIZATION_QUERY)),
                page(OaPage.EMPLOYEE_FILES, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(number("employeeId")), read(HR_ORGANIZATION_QUERY), read(HR_EMPLOYEE_QUERY)),
                page(OaPage.EMPLOYEE_CHANGE, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("status"), text("changeType"), text("keyword"), number("page"), number("size")),
                        read(HR_CHANGE_QUERY)),

                page(OaPage.ASSET_LEDGER, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("keyword"), text("category"), text("status"), number("page"), number("size")),
                        read(ASSET_QUERY), write(ASSET_CLAIM), write(ASSET_RETURN), write(ASSET_REPAIR_START)),
                page(OaPage.MEETING_ROOM, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("keyword"), text("roomStatus"), text("from"), text("to"),
                                text("bookingStatus"), number("page"), number("size")),
                        read(MEETING_QUERY), write(MEETING_BOOK), write(MEETING_CANCEL)),
                page(OaPage.VISITOR_BOOKING, OwnershipPolicy.SELF, LIST_COMMANDS,
                        context(number("bookingId"), text("queue"), text("status"), number("page"), number("size")),
                        read(VISITOR_QUERY)),
                page(OaPage.SEAL_USAGE, OwnershipPolicy.SELF, LIST_COMMANDS,
                        context(number("usageId"), text("queue"), text("status"), number("page"), number("size")),
                        read(SEAL_QUERY)),

                page(OaPage.EXPENSE, OwnershipPolicy.SELF, FORM_COMMANDS,
                        context(number("applicationId"), text("status"), number("page"), number("size")),
                        read(EXPENSE_QUERY)),
                page(OaPage.BUDGET, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(number("budgetId"), text("keyword"), text("status"), number("fiscalYear"),
                                number("page"), number("size")), read(BUDGET_QUERY)),
                page(OaPage.CONTRACTS, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(number("contractId"), text("keyword"), text("status"), text("contractType"),
                                text("expiryState"), number("page"), number("size")), read(CONTRACT_QUERY)),
                page(OaPage.SUPPLIERS, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(number("supplierId"), text("keyword"), text("status"), text("category"),
                                number("page"), number("size")), read(SUPPLIER_QUERY)),

                page(OaPage.API_CENTER, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(number("endpointId"), text("keyword"), text("status"), number("page"), number("size")),
                        read(INTEGRATION_ENDPOINT_QUERY)),
                page(OaPage.PAGE_ACTIONS, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("targetPageId"), text("enabled"), number("page"), number("size")),
                        read(PAGE_ACTION_QUERY)),
                page(OaPage.RUNTIME_LOGS, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("source"), number("recordId"), text("outcome"), text("keyword"),
                                text("from"), text("to"), number("page"), number("size")), read(RUNTIME_LOG_QUERY)),
                page(OaPage.SANDBOX_REPLAY, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(number("replayId"), text("keyword"), text("status"), number("page"), number("size")),
                        read(SANDBOX_REPLAY_QUERY)),

                page(OaPage.ATTENDANCE_CLOCK, OwnershipPolicy.SELF, READ_COMMANDS,
                        context(text("resource")), read(ATTENDANCE_QUERY)),
                page(OaPage.ATTENDANCE_EXCEPTION, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        attendanceListContext(), read(ATTENDANCE_QUERY)),
                page(OaPage.ATTENDANCE_REISSUE, OwnershipPolicy.SELF, LIST_COMMANDS,
                        context(text("resource"), text("status"), number("page"), number("size")),
                        read(ATTENDANCE_QUERY), write(ATTENDANCE_REISSUE_APPLY)),
                page(OaPage.ATTENDANCE_STATISTICS, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("resource"), number("year"), number("month")), read(ATTENDANCE_QUERY)),
                page(OaPage.ATTENDANCE_SETTINGS, OwnershipPolicy.TENANT_SCOPED, READ_COMMANDS,
                        context(text("resource")), read(ATTENDANCE_QUERY)),

                page(OaPage.ACCESS_CONTROL, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("filterCode")), read(ACCESS_GOVERNANCE_QUERY)),
                page(OaPage.DATA_PERMISSION, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("scopeType"), text("enabled")), read(DATA_PERMISSION_QUERY)),
                page(OaPage.AI_PERMISSION, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("toolCode"), text("filterCode"), text("effectiveEnabled")), read(AI_PERMISSION_QUERY)),
                page(OaPage.KNOWLEDGE_BASE, OwnershipPolicy.FIXED_RESOURCE, LIST_COMMANDS,
                        context(text("query"), number("topK"), number("minScore")), read(KNOWLEDGE_SEARCH)),
                page(OaPage.AUDIT_CENTER, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("action"), text("resourceType"), text("result"), text("from"), text("to"),
                                number("page"), number("size")), read(AUDIT_QUERY)),
                page(OaPage.TENANT_CONFIG, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(), read(TENANT_CONFIGURATION_QUERY)),
                page(OaPage.DICTIONARY, OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("keyword"), text("status")), read(DICTIONARY_QUERY)),
                page(OaPage.SYSTEM_CONFIG, OwnershipPolicy.SELF, READ_COMMANDS,
                        context(), read(SYSTEM_CAPABILITY_QUERY))
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

    private PageToolReference read(ToolCode code) {
        return new PageToolReference(code, READ);
    }

    private PageToolReference write(ToolCode code) {
        return new PageToolReference(code, SINGLE_WRITE);
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
