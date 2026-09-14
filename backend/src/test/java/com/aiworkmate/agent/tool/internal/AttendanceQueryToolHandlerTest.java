package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.AttendanceToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendanceQueryToolHandlerTest {
    @Test
    void usesTrustedActorParsesBoundedViewAndOmitsAbsentSensitiveData() throws Exception {
        AttendanceToolPort port = mock(AttendanceToolPort.class);
        var context = new TrustedToolContext(1L, 7L, 10L, 20L, 1, "trace");
        var query = new AttendanceToolPort.Query(AttendanceToolPort.Resource.EXCEPTIONS,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 9L,
                null, null, null, 2, 50);
        when(port.query(context.actor(), query)).thenReturn(new AttendanceToolPort.Result(
                AttendanceToolPort.Resource.EXCEPTIONS, null,
                List.of(new AttendanceToolPort.Record(3L, "张三", LocalDate.of(2026, 9, 2),
                        null, null, "LATE", 10, 0)), List.of(), null, null, 1, 2, 50));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        var output = new AttendanceQueryToolHandler(port, mapper).execute(context, mapper.readTree(
                "{\"resource\":\"EXCEPTIONS\",\"from\":\"2026-09-01\",\"to\":\"2026-09-30\",\"employeeId\":9,\"page\":2,\"size\":500}"));

        assertThat(output.at("/records/0/employeeName").asText()).isEqualTo("张三");
        assertThat(output.has("today")).isFalse();
        assertThat(output.toString()).doesNotContain("userId", "tenantId", "clockInIp");
        verify(port).query(context.actor(), query);
    }
}
