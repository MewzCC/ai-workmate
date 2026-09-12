package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.EmployeeChangeToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EmployeeChangeQueryToolHandlerTest {
    @Test
    void usesTrustedActorCapsPageAndReturnsSafeFields() throws Exception {
        EmployeeChangeToolPort port = mock(EmployeeChangeToolPort.class);
        var context = new TrustedToolContext(1L, 7L, 1L, 1L, 1, "trace");
        var query = new EmployeeChangeToolPort.Query("PENDING", "TRANSFER", "张三", 2, 50);
        when(port.query(context.actor(), query)).thenReturn(new EmployeeChangeToolPort.Page(List.of(
                new EmployeeChangeToolPort.Item(9L, "张三", "人事", "李经理", "TRANSFER",
                        LocalDate.of(2026, 10, 1), "研发部", "工程师", "平台部", "高级工程师",
                        "王总", "组织调整", "PENDING", 1, true, false, null, null, null)), 1, 2, 50));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var output = new EmployeeChangeQueryToolHandler(port, mapper).execute(
                context, mapper.readTree(
                        "{\"status\":\"PENDING\",\"changeType\":\"TRANSFER\",\"keyword\":\"张三\",\"page\":2,\"size\":500}"));
        assertThat(output.at("/items/0/employeeName").asText()).isEqualTo("张三");
        assertThat(output.toString()).doesNotContain("employeeUserId", "applicantUserId", "tenantId");
        verify(port).query(context.actor(), query);
    }
}
