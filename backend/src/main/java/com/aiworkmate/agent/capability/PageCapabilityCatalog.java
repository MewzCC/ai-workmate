package com.aiworkmate.agent.capability;

import com.aiworkmate.agent.registry.OwnershipPolicy;
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

    public String canonicalPageId(String pageId) {
        if (pageId == null) return null;
        return LEGACY_PAGE_ALIASES.getOrDefault(pageId, pageId);
    }

    private List<PageCapabilityDefinition> pages() {
        return List.of(
                page("dashboard", "DASHBOARD", OwnershipPolicy.SELF, LIST_COMMANDS,
                        context(text("status"), number("page"), number("size")),
                        read("todo.query"), read("notification.mine")),
                page("ai-workspace", "AI_WORKSPACE", OwnershipPolicy.SELF, READ_COMMANDS, PageContextSchema.empty(),
                        read("todo.query"), read("leave.mine"), read("knowledge.search"), read("notification.mine"),
                        write("leave.createDraft"), write("leave.submit"), write("leave.apply")),
                page("ai-tasks", "AI_TASK_CENTER", OwnershipPolicy.SELF, LIST_COMMANDS),
                page("todo", "TODO_LIST", OwnershipPolicy.ASSIGNED_TO_SELF, LIST_COMMANDS,
                        context(text("status"), text("from"), text("to"), number("page"), number("size")),
                        read("todo.query")),
                page("messages", "MESSAGE_CENTER", OwnershipPolicy.SELF, LIST_COMMANDS,
                        context(number("page"), number("size")), read("notification.mine")),
                page("leave-application", "LEAVE_FORM", OwnershipPolicy.SELF, FORM_COMMANDS),
                page("my-applications", "MY_APPLICATIONS", OwnershipPolicy.SELF, LIST_COMMANDS,
                        context(text("applicationId"), text("status"), number("page"), number("size")),
                        read("leave.mine"), write("leave.createDraft"), write("leave.submit"), write("leave.apply")),

                page("approval-list", "APPROVAL_LIST", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        read("approval.task.query")),
                page("approval-start", "APPROVAL_START", OwnershipPolicy.SELF, READ_COMMANDS,
                        read("approval.configuration.query")),
                page("approval-form", "APPROVAL_FORM", OwnershipPolicy.SELF, FORM_COMMANDS,
                        read("approval.configuration.query")),
                page("form-engine", "FORM_ENGINE", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        read("approval.configuration.query")),
                page("process-config", "PROCESS_CONFIG", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        read("approval.configuration.query")),
                page("approval-rules", "APPROVAL_RULES", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        read("approval.configuration.query")),

                page("org-tree", "ORG_TREE", OwnershipPolicy.TENANT_SCOPED, READ_COMMANDS,
                        context(text("keyword"), number("limit")), read("hr.organization.query")),
                page("employee-files", "EMPLOYEE_FILES", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(number("employeeId")), read("hr.organization.query"), read("hr.employee.query")),
                page("employee-change", "EMPLOYEE_CHANGE", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS,
                        context(text("status"), text("changeType"), text("keyword"), number("page"), number("size")),
                        read("hr.change.query")),

                page("asset-ledger", "ASSET_LEDGER", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS),
                page("meeting-room", "MEETING_ROOM", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS),
                page("visitor-booking", "VISITOR_BOOKING", OwnershipPolicy.SELF, LIST_COMMANDS),
                page("seal-usage", "SEAL_USAGE", OwnershipPolicy.SELF, LIST_COMMANDS),

                page("expense", "EXPENSE", OwnershipPolicy.SELF, FORM_COMMANDS),
                page("budget", "BUDGET", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS),
                page("contracts", "CONTRACT", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS),
                page("suppliers", "SUPPLIER", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS),

                page("api-center", "API_CENTER", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS),
                page("page-actions", "PAGE_ACTIONS", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS),
                page("runtime-logs", "RUNTIME_LOGS", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS),
                page("sandbox-replay", "SANDBOX_REPLAY", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS),

                page("attendance-clock", "ATTENDANCE_CLOCK", OwnershipPolicy.SELF, READ_COMMANDS),
                page("attendance-exception", "ATTENDANCE_EXCEPTION", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS),
                page("attendance-reissue", "ATTENDANCE_REISSUE", OwnershipPolicy.SELF, LIST_COMMANDS),
                page("attendance-statistics", "ATTENDANCE_STATISTICS", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS),
                page("attendance-settings", "ATTENDANCE_SETTINGS", OwnershipPolicy.TENANT_SCOPED, READ_COMMANDS),

                page("access-control", "ACCESS_CONTROL", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS),
                page("data-permission", "DATA_PERMISSION", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS),
                page("ai-permission", "AI_PERMISSION", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS),
                page("knowledge-base", "KNOWLEDGE_BASE", OwnershipPolicy.FIXED_RESOURCE, LIST_COMMANDS,
                        context(text("query"), number("topK"), number("minScore")), read("knowledge.search")),
                page("audit-center", "AUDIT_CENTER", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS),
                page("tenant-config", "TENANT_CONFIG", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS),
                page("dictionary", "DICTIONARY", OwnershipPolicy.TENANT_SCOPED, LIST_COMMANDS),
                page("system-config", "SYSTEM_CONFIG", OwnershipPolicy.SELF, READ_COMMANDS)
        );
    }

    private PageCapabilityDefinition page(String pageId, String componentKey, OwnershipPolicy scope,
                                          Set<PageUiCommand> commands, PageToolReference... tools) {
        return page(pageId, componentKey, scope, commands, PageContextSchema.empty(), tools);
    }

    private PageCapabilityDefinition page(String pageId, String componentKey, OwnershipPolicy scope,
                                          Set<PageUiCommand> commands, PageContextSchema context,
                                          PageToolReference... tools) {
        return new PageCapabilityDefinition(pageId, componentKey, 1, commands, List.of(tools),
                Set.of("route:" + pageId), scope, context);
    }

    private PageToolReference read(String code) {
        return new PageToolReference(code, READ);
    }

    private PageToolReference write(String code) {
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
}
