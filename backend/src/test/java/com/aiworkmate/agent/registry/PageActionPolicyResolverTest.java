package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.capability.PageCapabilityCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PageActionPolicyResolverTest {

    @Test
    void readsLegacyClientPageNamesFromCanonicalPolicyRows() {
        AgentPageActionPolicyMapper mapper = mock(AgentPageActionPolicyMapper.class);
        PageActionCatalog catalog = new PageActionCatalog(
                new PageCapabilityCatalog(), new ToolCatalog(List.of()));
        AgentPageActionPolicy policy = new AgentPageActionPolicy();
        policy.setToolCode("todo.query");
        policy.setEnabled(false);
        when(mapper.selectByTenantAndPage(9L, "todo")).thenReturn(List.of(policy));

        PageActionPolicyResolver resolver = new PageActionPolicyResolver(catalog, mapper);

        assertThat(resolver.enabledToolCodes(9L, "todo-list")).isEmpty();
        verify(mapper).selectByTenantAndPage(9L, "todo");
    }
}
