package com.aiworkmate.service.impl;

import com.aiworkmate.agent.registry.AgentPageActionPolicy;
import com.aiworkmate.agent.registry.AgentPageActionPolicyMapper;
import com.aiworkmate.agent.registry.PageActionCatalog;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.PageActionOverviewResponse;
import com.aiworkmate.dto.UpdatePageActionPolicyRequest;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.PageActionPolicyService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PageActionPolicyServiceImpl implements PageActionPolicyService {
    private static final String READ = "route:page-actions";
    private static final String MANAGE = "page-action:manage";

    private final PageActionCatalog catalog;
    private final AgentPageActionPolicyMapper mapper;
    private final UserAccessService accessService;
    private final BusinessAuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public PageActionOverviewResponse overview(Long userId) {
        ResolvedUserAccess actor = requireRead(userId);
        Map<String, AgentPageActionPolicy> policies = mapper.selectByTenant(actor.tenantId()).stream()
                .collect(Collectors.toMap(this::key, Function.identity(), (left, right) -> left));
        Map<String, List<PageActionOverviewResponse.Action>> pages = new LinkedHashMap<>();
        catalog.all().forEach(binding -> {
            AgentPageActionPolicy policy = policies.get(key(binding.pageId(), binding.tool().code()));
            boolean enabled = policy == null || Boolean.TRUE.equals(policy.getEnabled());
            PageActionOverviewResponse.Action action = new PageActionOverviewResponse.Action(
                    binding.pageId(), binding.tool().code(), binding.tool().name(), binding.tool().description(),
                    binding.tool().riskLevel().name(), binding.tool().sideEffect().name(),
                    binding.tool().confirmationPolicy().name(), binding.tool().requiredPermissions().stream().sorted().toList(),
                    enabled, policy != null, policy == null ? 0 : policy.getVersion());
            pages.computeIfAbsent(binding.pageId(), ignored -> new ArrayList<>()).add(action);
        });
        List<PageActionOverviewResponse.Page> result = pages.entrySet().stream()
                .map(entry -> new PageActionOverviewResponse.Page(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
        int total = result.stream().mapToInt(page -> page.actions().size()).sum();
        int enabled = (int) result.stream().flatMap(page -> page.actions().stream())
                .filter(PageActionOverviewResponse.Action::enabled).count();
        return new PageActionOverviewResponse(result, total, enabled, total - enabled,
                actor.permissions().contains(MANAGE));
    }

    @Override
    @Transactional
    public PageActionOverviewResponse update(Long userId, String pageId, String toolCode,
                                             UpdatePageActionPolicyRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        String normalizedPage = pageId == null ? "" : pageId.trim();
        String normalizedTool = toolCode == null ? "" : toolCode.trim();
        if (!catalog.contains(normalizedPage, normalizedTool)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        AgentPageActionPolicy existing = mapper.selectExact(actor.tenantId(), normalizedPage, normalizedTool);
        if (existing == null) {
            if (request.version() != 0) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
            AgentPageActionPolicy created = new AgentPageActionPolicy();
            created.setTenantId(actor.tenantId());
            created.setPageId(normalizedPage);
            created.setToolCode(normalizedTool);
            created.setEnabled(request.enabled());
            created.setVersion(1);
            created.setUpdatedBy(actor.userId());
            LocalDateTime now = LocalDateTime.now();
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            try {
                mapper.insert(created);
            } catch (DuplicateKeyException exception) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT);
            }
        } else {
            if (!request.version().equals(existing.getVersion())
                    || mapper.updateEnabled(actor.tenantId(), normalizedPage, normalizedTool,
                    request.enabled(), actor.userId(), existing.getVersion()) != 1) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT);
            }
        }
        auditService.recordTransactional(actor.tenantId(), actor.userId(), "AGENT_PAGE_ACTION",
                normalizedPage + ":" + normalizedTool, "UPDATE_PAGE_ACTION_POLICY", "SUCCESS",
                "enabled=" + request.enabled() + "; reason=" + request.reason().trim());
        return overview(userId);
    }

    private ResolvedUserAccess requireRead(Long userId) {
        ResolvedUserAccess actor = accessService.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        if (!actor.permissions().contains(READ)) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        return actor;
    }

    private ResolvedUserAccess requireManage(Long userId) {
        ResolvedUserAccess actor = requireRead(userId);
        if (!actor.permissions().contains(MANAGE)) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        return actor;
    }

    private String key(AgentPageActionPolicy policy) {
        return key(policy.getPageId(), policy.getToolCode());
    }

    private String key(String pageId, String toolCode) {
        return pageId + "\n" + toolCode;
    }
}
