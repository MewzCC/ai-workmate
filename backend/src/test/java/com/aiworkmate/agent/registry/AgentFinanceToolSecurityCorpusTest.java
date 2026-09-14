package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentFinanceToolSecurityCorpusTest {
    @Test
    void rejectsIdentityNetworkSqlNestedAndOverLimitArgumentsForEveryFinanceTool() throws Exception {
        var mapper = new ObjectMapper();
        var validator = new ToolSchemaValidator();
        var d = new AgentFinanceToolDefinitions();
        List<ToolDefinition> tools = List.of(d.expenseQueryToolDefinition(mapper), d.budgetQueryToolDefinition(mapper),
                d.contractQueryToolDefinition(mapper), d.supplierQueryToolDefinition(mapper));
        for (ToolDefinition tool : tools) {
            for (String json : List.of("{\"userId\":7}", "{\"tenantId\":7}",
                    "{\"url\":\"https://evil.invalid\"}", "{\"sql\":\"select * from users\"}",
                    "{\"filter\":{\"nested\":true}}", "{\"size\":51}")) {
                assertThat(validator.valid(tool.inputSchema(), mapper.readTree(json)))
                        .as(tool.code() + " rejects " + json).isFalse();
            }
        }
    }
}
