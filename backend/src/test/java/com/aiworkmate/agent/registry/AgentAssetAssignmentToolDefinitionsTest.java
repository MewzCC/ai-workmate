package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AgentAssetAssignmentToolDefinitionsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AgentAssetAssignmentToolDefinitions definitions = new AgentAssetAssignmentToolDefinitions();
    private final ToolSchemaValidator validator = new ToolSchemaValidator();

    @Test void bothQueriesAreBoundedTenantReadsUsingAssetPermissionOnly() throws Exception {
        for (var tool : List.of(definitions.assetDepartmentQuery(json), definitions.assetCustodianQuery(json))) {
            assertThat(tool.requiredPermissions()).containsExactly("asset:write");
            assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L0);
            assertThat(tool.sideEffect()).isEqualTo(SideEffect.NONE);
            assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
            assertThat(tool.maxResultItems()).isEqualTo(20);
            assertThat(tool.outputSchema().toString()).doesNotContain("email", "password", "roles", "permissions");
        }
        assertThat(definitions.assetDepartmentQuery(json).schemaHash()).isEqualTo("sha256:a9ea3f82ecb378d78a566cb44f7f0eb4d51010fd9de1bf22752071de5f278e8f");
        assertThat(definitions.assetCustodianQuery(json).schemaHash()).isEqualTo("sha256:a8a7b1fef0eca2d1fb667377d269e8573cf888e0d4e907c8abaed7dfbe815841");
    }

    @Test void rejectsAuthorityInjectionAndOutOfBudgetQueries() throws Exception {
        var tool = definitions.assetCustodianQuery(json);
        assertThat(validator.valid(tool.inputSchema(), json.readTree("{\"departmentId\":5,\"keyword\":\"Alice\",\"page\":1,\"size\":20}"))).isTrue();
        for (String input : List.of("{}", "{\"departmentId\":0}", "{\"departmentId\":5,\"size\":21}",
                "{\"departmentId\":5,\"tenantId\":2}", "{\"departmentId\":5,\"userId\":1}", "{\"departmentId\":5,\"page\":10001}")) {
            assertThat(validator.valid(tool.inputSchema(), json.readTree(input))).as(input).isFalse();
        }
        assertThat(validator.valid(tool.inputSchema(), json.createObjectNode().put("departmentId", 5).put("keyword", "x".repeat(121)))).isFalse();
        assertThat(validator.valid(definitions.assetDepartmentQuery(json).inputSchema(), json.createObjectNode())).isTrue();
    }
}
