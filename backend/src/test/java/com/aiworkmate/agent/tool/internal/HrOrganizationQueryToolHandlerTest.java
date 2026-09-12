package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.HrOrganizationToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HrOrganizationQueryToolHandlerTest {
    private final HrOrganizationToolPort port = mock(HrOrganizationToolPort.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final HrOrganizationQueryToolHandler handler = new HrOrganizationQueryToolHandler(port, mapper);

    @Test
    void usesTrustedActorCapsLimitAndReturnsSafeOrganizationFields() throws Exception {
        var context = new TrustedToolContext(99L, 7L, 1L, 2L, 1, "trace");
        var query = new HrOrganizationToolPort.Query("研发", 50);
        when(port.query(context.actor(), query)).thenReturn(new HrOrganizationToolPort.Result(
                List.of(new HrOrganizationToolPort.Department(1L, "RD", "研发部", null, 1)),
                List.of(new HrOrganizationToolPort.Position(2L, "DEV", "研发工程师", 1)),
                List.of(new HrOrganizationToolPort.Employee(3L, "张三", "EMPLOYEE", 1, 1L, 2L, "李经理"))));

        var output = handler.execute(context,
                mapper.readTree("{\"keyword\":\"研发\",\"limit\":500}"));

        assertThat(output.at("/departments/0/name").asText()).isEqualTo("研发部");
        assertThat(output.at("/employees/0/name").asText()).isEqualTo("张三");
        assertThat(output.toString()).doesNotContain("email", "avatar", "permission", "tenantId");
        verify(port).query(context.actor(), query);
    }
}
