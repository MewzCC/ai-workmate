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

        assertThat(resolver.enabledToolCodes(9L, "todo-list")).containsExactlyInAnyOrder("page.ui.prepare", "approval.genericTask.detail.query", "approval.genericTask.approve", "approval.genericTask.reject", "approval.genericTask.participants.query", "approval.genericTask.transfer", "approval.genericTask.cc");
        verify(mapper).selectByTenantAndPage(9L, "todo");
        AgentPageActionPolicy detail = new AgentPageActionPolicy();
        detail.setToolCode("approval.genericTask.detail.query");
        detail.setEnabled(false);
        when(mapper.selectByTenantAndPage(9L, "todo")).thenReturn(List.of(policy, detail));
        assertThat(resolver.enabledToolCodes(9L, "todo-list")).containsExactlyInAnyOrder("page.ui.prepare", "approval.genericTask.approve", "approval.genericTask.reject", "approval.genericTask.participants.query", "approval.genericTask.transfer", "approval.genericTask.cc");
        AgentPageActionPolicy approve = new AgentPageActionPolicy();
        approve.setToolCode("approval.genericTask.approve");
        approve.setEnabled(false);
        when(mapper.selectByTenantAndPage(9L, "todo")).thenReturn(List.of(policy, detail, approve));
        assertThat(resolver.enabledToolCodes(9L, "todo-list")).containsExactlyInAnyOrder("page.ui.prepare", "approval.genericTask.reject", "approval.genericTask.participants.query", "approval.genericTask.transfer", "approval.genericTask.cc");
    }
}
