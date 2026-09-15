package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StableToolOperationKeyTest {
    @Test
    void preservesExistingFormatAcrossAttemptsAndTraceChanges() {
        var first = new TrustedToolContext(1, 7, 10, 20, 1, "first");
        var retry = new TrustedToolContext(1, 7, 10, 20, 2, "retry");
        assertThat(StableToolOperationKey.v1(first, ToolCode.MEETING_BOOK))
                .isEqualTo("agent:10:20:meeting.book:v1")
                .isEqualTo(StableToolOperationKey.v1(retry, ToolCode.MEETING_BOOK));
        assertThat(StableToolOperationKey.v1(first, ToolCode.MEETING_CANCEL))
                .isNotEqualTo(StableToolOperationKey.v1(first, ToolCode.MEETING_BOOK));
    }

    @Test
    void rejectsMissingOrInvalidTrustedCoordinates() {
        assertThatThrownBy(() -> StableToolOperationKey.v1(null, ToolCode.LEAVE_APPLY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StableToolOperationKey.v1(
                new TrustedToolContext(1, 7, 0, 20, 1, "trace"), ToolCode.LEAVE_APPLY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StableToolOperationKey.v1(
                new TrustedToolContext(1, 7, 10, 20, 1, "trace"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
