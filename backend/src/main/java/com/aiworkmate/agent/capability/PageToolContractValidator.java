package com.aiworkmate.agent.capability;

import com.aiworkmate.agent.registry.SideEffect;
import com.aiworkmate.agent.registry.ToolCatalog;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates the two existing code-owned catalogs; never grants runtime permission. */
@Component
public final class PageToolContractValidator {
    public PageToolContractValidator(PageCapabilityCatalog pages, ToolCatalog tools) {
        Set<String> bound = new HashSet<>();
        for (var page : pages.all()) {
            for (var reference : page.tools()) {
                var definition = tools.find(reference.toolCode()).orElseThrow(() ->
                        new IllegalStateException("Page references unregistered tool: "
                                + page.pageId() + "/" + reference.toolCode()));
                boolean write = reference.access() == PageToolAccess.SINGLE_WRITE;
                if (write != (definition.sideEffect() == SideEffect.SINGLE_WRITE)) {
                    throw new IllegalStateException("Page/tool side effect mismatch: "
                            + page.pageId() + "/" + reference.toolCode());
                }
                bound.add(reference.toolCode());
            }
        }
        var registered = tools.all().stream().map(tool -> tool.code()).collect(Collectors.toSet());
        if (!bound.equals(registered)) {
            throw new IllegalStateException("Registered tools must have explicit page bindings");
        }
    }
}
