package com.aiworkmate.agent.registry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCodeTest {

    @Test
    void resolvesEveryCodeFromTheCodeOwnedCatalog() {
        assertThat(ToolCode.codes()).hasSize(ToolCode.values().length);
        for (ToolCode value : ToolCode.values()) {
            assertThat(ToolCode.fromCode(value.code())).isSameAs(value);
            assertThat(ToolCode.isSupported(value.code())).isTrue();
            assertThat(value.isWrite()).isEqualTo(value.sideEffect() == SideEffect.SINGLE_WRITE);
        }
        assertThat(ToolCode.TODO_QUERY.isWrite()).isFalse();
        assertThat(ToolCode.EXPENSE_CREATE_DRAFT.isWrite()).isTrue();
    }

    @Test
    void rejectsCodesOutsideThePlatformBoundary() {
        assertThat(ToolCode.isSupported("sql.execute")).isFalse();
        assertThatThrownBy(() -> ToolCode.fromCode("sql.execute"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Phase 2 capability boundary");
    }
}
