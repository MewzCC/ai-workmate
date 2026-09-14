package com.aiworkmate.agent.registry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PageActionPolicyResolver {
    private final PageActionCatalog catalog;
    private final AgentPageActionPolicyMapper mapper;

    public Set<String> enabledToolCodes(Long tenantId, String pageId) {
        String canonicalPageId = catalog.canonicalPageId(pageId);
        Set<String> baseline = catalog.toolCodes(canonicalPageId);
        if (tenantId == null || baseline.isEmpty()) return Set.of();
        Map<String, AgentPageActionPolicy> policies = mapper.selectByTenantAndPage(tenantId, canonicalPageId).stream()
                .collect(Collectors.toMap(AgentPageActionPolicy::getToolCode, Function.identity(), (left, right) -> left));
        return baseline.stream()
                .filter(code -> !policies.containsKey(code) || Boolean.TRUE.equals(policies.get(code).getEnabled()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
