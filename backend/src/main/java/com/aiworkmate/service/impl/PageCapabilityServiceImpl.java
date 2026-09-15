package com.aiworkmate.service.impl;

import com.aiworkmate.agent.capability.PageCapabilityCatalog;
import com.aiworkmate.agent.capability.PageCapabilityDefinition;
import com.aiworkmate.agent.capability.PageUiCommand;
import com.aiworkmate.agent.registry.ToolDefinition;
import com.aiworkmate.agent.registry.ToolRegistry;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.PageCapabilityResponse;
import com.aiworkmate.service.PageCapabilityService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PageCapabilityServiceImpl implements PageCapabilityService {
    private final UserAccessService userAccessService;
    private final PageCapabilityCatalog pageCapabilityCatalog;
    private final ToolRegistry toolRegistry;

    @Override
    public PageCapabilityResponse resolve(Long userId, String pageId) {
        PageCapabilityDefinition page = pageCapabilityCatalog.find(pageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        ResolvedUserAccess access = userAccessService.resolveActiveUser(userId);
        if (!access.permissions().containsAll(page.requiredPermissions())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }

        List<String> commands = page.uiCommands().stream()
                .map(PageUiCommand::code)
                .sorted()
                .toList();
        List<String> scopes = access.dataScopes().stream().sorted().toList();
        List<PageCapabilityResponse.Tool> tools = toolRegistry.resolveAllowedTools(access, page.pageId()).stream()
                .sorted(Comparator.comparing(ToolDefinition::code))
                .map(this::toResponse)
                .toList();
        return new PageCapabilityResponse(
                page.pageId(),
                page.componentKey(),
                page.version(),
                commands,
                page.dataScopePolicy().name(),
                scopes,
                tools,
                tools.isEmpty() ? PageCapabilityResponse.UnavailableReason.NO_AVAILABLE_TOOLS : null
        );
    }

    private PageCapabilityResponse.Tool toResponse(ToolDefinition tool) {
        return new PageCapabilityResponse.Tool(
                tool.code(),
                tool.name(),
                tool.description(),
                tool.riskLevel().name(),
                tool.sideEffect().name(),
                tool.confirmationPolicy().name(),
                tool.ownershipPolicy().name()
        );
    }
}
