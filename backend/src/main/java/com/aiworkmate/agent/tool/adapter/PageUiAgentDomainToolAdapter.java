package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.PageUiToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.service.PageCapabilityService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.PageUiTaskSnapshotService;
import lombok.RequiredArgsConstructor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public final class PageUiAgentDomainToolAdapter implements PageUiToolPort {
    private static final Set<String> FORBIDDEN = Set.of("userid", "tenantid", "role", "roles", "permission", "permissions",
            "datascope", "url", "uri", "sql", "file", "filepath", "path", "script", "classname", "beanname",
            "__proto__", "prototype", "constructor", "token", "password", "secret");
    private final PageCapabilityService capabilities;
    private final UserAccessService accessService;
    private final PageUiTaskSnapshotService snapshots;

    @Override
    public Prepared prepare(ToolActorContext context, Command command) {
        var actor = accessService.resolveActiveUser(context.userId());
        if (actor == null || !actor.tenantId().equals(context.tenantId())
                || !actor.permissions().contains("agent:ui:prepare")) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        var task = snapshots.runningSnapshot(context.tenantId(), context.userId(), context.taskId());
        if (command == null || command.pageId() == null || !command.pageId().matches("^[a-z][a-z0-9-]{1,59}$")
                || command.code() == null || command.fields().size() > 32) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        if (!"ai-workspace".equals(task.pageId()) && !task.pageId().equals(command.pageId()))
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        var capability = capabilities.resolve(context.userId(), command.pageId());
        if (!capability.uiCommands().contains(command.code())) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        boolean fieldsCommand = Set.of("ui.applyFilter", "ui.fillForm").contains(command.code());
        if (!fieldsCommand && !command.fields().isEmpty() || fieldsCommand && command.fields().isEmpty()
                || !"ui.navigate".equals(command.code()) && command.targetPageId() != null
                || !"ui.openDetail".equals(command.code()) && command.recordId() != null)
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        if ("ui.navigate".equals(command.code())) {
            if (command.targetPageId() == null || !command.targetPageId().matches("^[a-z][a-z0-9-]{1,59}$"))
                throw new BusinessException(ErrorCode.REQUEST_INVALID);
            capabilities.resolve(context.userId(), command.targetPageId());
        }
        if ("ui.openDetail".equals(command.code()) && (!Command.validRecordId(command.recordId())
                || command.recordId() instanceof String && !"ai-tasks".equals(command.pageId())))
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        Set<String> seen = new HashSet<>();
        List<String> filters = capability.contextSchema().fields().stream().map(field -> field.name())
                .filter(name -> !name.startsWith("ui")).toList();
        for (Field field : command.fields()) {
            if (field == null || field.name() == null || !field.name().matches("^[a-z][A-Za-z0-9]{0,39}$")
                    || FORBIDDEN.contains(field.name().toLowerCase(java.util.Locale.ROOT)) || !seen.add(field.name())
                    || field.value() instanceof String text && text.length() > 500
                    || field.value() instanceof java.math.BigDecimal number && number.abs().compareTo(new java.math.BigDecimal("1000000000000")) > 0
                    || "ui.applyFilter".equals(command.code()) && !filters.contains(field.name()))
                throw new BusinessException(ErrorCode.REQUEST_INVALID);
            if ("view".equals(field.name())) {
                Set<String> views = switch (command.pageId()) {
                    case "my-applications" -> Set.of("leave", "generic");
                    case "dictionary" -> Set.of("types", "items");
                    case "knowledge-base" -> Set.of("list", "overview", "docs", "query", "settings");
                    default -> Set.of();
                };
                if (!"ui.applyFilter".equals(command.code()) || !(field.value() instanceof String view) || !views.contains(view))
                    throw new BusinessException(ErrorCode.REQUEST_INVALID);
            }
        }
        FormContext formContext = null;
        if (Set.of("ui.fillForm", "ui.previewSubmission").contains(command.code()) && task.pageId().equals(command.pageId()) && task.formSession() != null) {
            formContext = new FormContext(task.formSession(), task.recordId(), task.version());
        }
        return new Prepared(command.pageId(), command.code(), command.targetPageId(), command.recordId(), command.fields(), formContext);
    }
}
