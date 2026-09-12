package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
    private LeaveToolPort leaveToolPort;

    private ObjectMapper objectMapper;
    private LeaveMineToolHandler handler;
    private TrustedToolContext context;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        handler = new LeaveMineToolHandler(leaveToolPort, objectMapper);
        context = new TrustedToolContext(91L, 7L, 10L, 20L, 1, "trace");
    }

    @Test
    void listsOnlySafeFieldsAndUsesTrustedUser() throws Exception {
        when(leaveToolPort.mine(context.actor(), new LeaveToolPort.Query("PENDING", 2, 50)))
                .thenReturn(new LeaveToolPort.Page(List.of(application()), 1, 2, 50));

        var result = handler.execute(context,
                objectMapper.readTree("{\"status\":\"PENDING\",\"page\":2,\"size\":500}"));

        assertThat(result.path("items")).hasSize(1);
        assertThat(result.at("/items/0/reason").asText()).isEqualTo("家庭事务");
        assertThat(result.at("/items/0/applicantUserId").isMissingNode()).isTrue();
        assertThat(result.at("/items/0/approverUserId").isMissingNode()).isTrue();
        assertThat(result.at("/items/0/taskId").isMissingNode()).isTrue();
        verify(leaveToolPort).mine(context.actor(), new LeaveToolPort.Query("PENDING", 2, 50));
    }

    @Test
    void detailUsesOwnedDomainOperation() throws Exception {
        when(leaveToolPort.getMine(context.actor(), 10L)).thenReturn(application());

        var result = handler.execute(context, objectMapper.readTree("{\"applicationId\":10}"));

        assertThat(result.path("total").asLong()).isEqualTo(1);
        assertThat(result.at("/items/0/id").asLong()).isEqualTo(10L);
        verify(leaveToolPort).getMine(context.actor(), 10L);
        verify(leaveToolPort, never()).mine(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsAmbiguousDetailAndListArgumentsBeforeDomainCall() throws Exception {
        assertThatThrownBy(() -> handler.execute(context,
                objectMapper.readTree("{\"applicationId\":10,\"page\":1}")))
                .isInstanceOf(BusinessException.class);

        verify(leaveToolPort, never()).getMine(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    private LeaveToolPort.Item application() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 25, 9, 30);
        return new LeaveToolPort.Item(
                10L, "直属主管", "PERSONAL",
                LocalDate.of(2026, 8, 26), "AM", LocalDate.of(2026, 8, 26), "PM",
                2, 1.0, "家庭事务", "PENDING", 1,
                now, null, now.minusDays(1), now);
    }
}
