package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalTaskQueryToolHandlerTest {
    private final ApprovalTaskToolPort port = mock(ApprovalTaskToolPort.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final ApprovalTaskQueryToolHandler handler = new ApprovalTaskQueryToolHandler(port, mapper);
    private final TrustedToolContext context = new TrustedToolContext(99L, 7L, 1L, 2L, 1, "trace");

    @Test
    void usesTrustedActorCapsPageAndReturnsOnlySafeSummary() throws Exception {
        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 9, 30, 23, 59);
        var query = new ApprovalTaskToolPort.Query("PENDING", from, to, "张三", "ANNUAL", 2, 50);
        when(port.query(context.actor(), query)).thenReturn(new ApprovalTaskToolPort.Page(List.of(
                new ApprovalTaskToolPort.Item(10L, 20L, "张三", "李经理", "ANNUAL", 1.5,
                        "PENDING", 3, from.plusDays(1), to.minusDays(1), true)), 1, 2, 50));

        var output = handler.execute(context, mapper.readTree("""
                {"status":"PENDING","from":"2026-09-01T00:00:00","to":"2026-09-30T23:59:00",
                 "keyword":"张三","leaveType":"ANNUAL","page":2,"size":500}
                """));

        assertThat(output.at("/items/0/applicantName").asText()).isEqualTo("张三");
        assertThat(output.at("/items/0/overdue").asBoolean()).isTrue();
        assertThat(output.toString()).doesNotContain("applicantUserId", "approverUserId", "dataJson");
        verify(port).query(context.actor(), query);
    }

    @Test
    void rejectsReversedDateRangeBeforePortCall() throws Exception {
        assertThatThrownBy(() -> handler.execute(context, mapper.readTree(
                "{\"from\":\"2026-09-30T00:00:00\",\"to\":\"2026-09-01T00:00:00\"}")))
                .isInstanceOf(BusinessException.class);
        verify(port, never()).query(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
