package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.HrEmployeeToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HrEmployeeQueryToolHandlerTest {
    @Test
    void usesTrustedActorAndOmitsNullAndSensitiveFields() throws Exception {
        HrEmployeeToolPort port = mock(HrEmployeeToolPort.class);
        var employee = new HrEmployeeToolPort.Employee(9L, "张三", "EMPLOYEE", 1,
                LocalDateTime.of(2026, 9, 1, 8, 0), "研发部", null, "李经理", List.of(),
                new HrEmployeeToolPort.Attendance(20, 18, 1, 0, 0, 1), List.of());
        when(port.get(7L, 9L)).thenReturn(employee);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var output = new HrEmployeeQueryToolHandler(port, mapper).execute(
                new TrustedToolContext(1L, 7L, 1L, 1L, 1, "trace"),
                mapper.readTree("{\"employeeId\":9}"));
        assertThat(output.path("name").asText()).isEqualTo("张三");
        assertThat(output.has("positionName")).isFalse();
        assertThat(output.toString()).doesNotContain("email", "avatar", "tenantId");
        verify(port).get(7L, 9L);
    }
}
