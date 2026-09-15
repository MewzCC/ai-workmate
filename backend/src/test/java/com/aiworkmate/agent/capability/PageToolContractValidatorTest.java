package com.aiworkmate.agent.capability;

import com.aiworkmate.agent.registry.SideEffect;
import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.registry.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PageToolContractValidatorTest {
    private final PageCapabilityCatalog pages = new PageCapabilityCatalog();
    private final ToolCatalog tools = mock(ToolCatalog.class);

    private void bindAll() {
        var definitions = new ArrayList<ToolDefinition>();
        var seen = new java.util.HashSet<String>();
        pages.all().forEach(page -> page.tools().forEach(reference -> {
            if (!seen.add(reference.toolCode())) return;
            var definition = mock(ToolDefinition.class);
            when(definition.code()).thenReturn(reference.toolCode());
            when(definition.sideEffect()).thenReturn(reference.access() == PageToolAccess.READ
                    ? SideEffect.NONE : SideEffect.SINGLE_WRITE);
            when(tools.find(reference.toolCode())).thenReturn(Optional.of(definition));
            definitions.add(definition);
        }));
        when(tools.all()).thenReturn(definitions);
    }

    @Test void acceptsConsistentExistingCatalogs() {
        bindAll();
        assertThatCode(() -> new PageToolContractValidator(pages, tools)).doesNotThrowAnyException();
    }

    @Test void rejectsMissingDefinition() {
        bindAll();
        when(tools.find("meeting.book")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new PageToolContractValidator(pages, tools))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("unregistered");
    }

    @Test void rejectsWriteMisclassifiedAsRead() {
        bindAll();
        when(tools.find("meeting.book").orElseThrow().sideEffect()).thenReturn(SideEffect.NONE);
        assertThatThrownBy(() -> new PageToolContractValidator(pages, tools))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("mismatch");
    }

    @Test void rejectsReadMisclassifiedAsWrite() {
        bindAll();
        when(tools.find("todo.query").orElseThrow().sideEffect()).thenReturn(SideEffect.SINGLE_WRITE);
        assertThatThrownBy(() -> new PageToolContractValidator(pages, tools))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("mismatch");
    }

    @Test void rejectsToolWithoutExplicitPageBinding() {
        bindAll();
        var definitions = new ArrayList<>(tools.all());
        var extra = mock(ToolDefinition.class);
        when(extra.code()).thenReturn("unbound.query");
        definitions.add(extra);
        when(tools.all()).thenReturn(definitions);
        assertThatThrownBy(() -> new PageToolContractValidator(pages, tools))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("bindings");
    }
}
