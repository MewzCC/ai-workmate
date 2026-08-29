package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.service.LeaveWorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveMineToolHandlerTest {
    @Mock
    private LeaveWorkflowService leaveWorkflowService;

    private ObjectMapper objectMapper;
    private LeaveMineToolHandler handler;
    private TrustedToolContext context;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new LeaveMineToolHandler(leaveWorkflowService, objectMapper);
        context = new TrustedToolContext(91L, 7L, 10L, 20L, 1, "trace");
    }

    @Test
    void listsOnlySafeFieldsAndUsesTrustedUser() throws Exception {
        when(leaveWorkflowService.mine(7L, "PENDING", 2, 50))
                .thenReturn(PageResponse.of(List.of(application()), 1, 2, 50));

        var result = handler.execute(context,
                objectMapper.readTree("{\"status\":\"PENDING\",\"page\":2,\"size\":500}"));

        assertThat(result.path("items")).hasSize(1);
        assertThat(result.at("/items/0/reason").asText()).isEqualTo("家庭事务");
        assertThat(result.at("/items/0/applicantUserId").isMissingNode()).isTrue();
        assertThat(result.at("/items/0/approverUserId").isMissingNode()).isTrue();
        assertThat(result.at("/items/0/taskId").isMissingNode()).isTrue();
        assertThat(result.toString()).doesNotContain("avatar-token");
        verify(leaveWorkflowService).mine(7L, "PENDING", 2, 50);
    }

    @Test
    void detailUsesOwnedDomainOperation() throws Exception {
        when(leaveWorkflowService.getMine(7L, 10L)).thenReturn(application());

        var result = handler.execute(context, objectMapper.readTree("{\"applicationId\":10}"));

        assertThat(result.path("total").asLong()).isEqualTo(1);
        assertThat(result.at("/items/0/id").asLong()).isEqualTo(10L);
        verify(leaveWorkflowService).getMine(7L, 10L);
        verify(leaveWorkflowService, never()).mine(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void rejectsAmbiguousDetailAndListArgumentsBeforeDomainCall() throws Exception {
        assertThatThrownBy(() -> handler.execute(context,
                objectMapper.readTree("{\"applicationId\":10,\"page\":1}")))
                .isInstanceOf(BusinessException.class);

        verify(leaveWorkflowService, never()).getMine(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    private LeaveApplicationResponse application() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 25, 9, 30);
        return new LeaveApplicationResponse(
                10L, 7L, "当前用户", 8L, "直属主管", "PERSONAL",
                LocalDate.of(2026, 8, 26), "AM", LocalDate.of(2026, 8, 26), "PM",
                2, 1.0, "家庭事务", "PENDING", 1,
                30L, 0, "PENDING", now.plusDays(2), false, 0, null, null, true,
                "RUNNING", "APPROVAL", List.of(), now, null, now.minusDays(1), now,
                false, false, true, false,
                "/avatar?token=avatar-token", "/avatar?token=avatar-token");
    }
}
