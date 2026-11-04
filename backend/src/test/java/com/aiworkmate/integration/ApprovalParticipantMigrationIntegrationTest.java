package com.aiworkmate.integration;

import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.registry.ToolDefinitionFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApprovalParticipantMigrationIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final String CODE = "approval.task.participants.query";
    private static final String OLD_HASH = "sha256:c1f565d9ed17668eb3c885274fcebdafdead56a51e4b560f329f6668eeca2dc3";
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper json;
    @Autowired ToolCatalog catalog;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        ENVIRONMENT.register(p); p.add("agent.enabled", () -> "false"); p.add("agent.execution-enabled", () -> "false");
    }
    @Test void validatesAnEmptyDatabaseThenUpgradesOnlyRecognizedExistingContractsWithoutEnablingAnything() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(ENVIRONMENT.schema());
        var definition = catalog.find(CODE).orElseThrow();
        assertContract(null); flyway().validate(); assertThat(flyway().info().pending()).isEmpty();
        ObjectNode oldInput = (ObjectNode) definition.inputSchema().deepCopy();
        ((ObjectNode) oldInput.path("properties")).remove(java.util.List.of("page", "size", "approversOnly"));
        ObjectNode oldOutput = (ObjectNode) definition.outputSchema().deepCopy();
        ((ObjectNode) oldOutput.path("properties")).remove(java.util.List.of("total", "page", "size", "approversOnly"));
        oldOutput.putArray("required").add("items");
        assertThat(ToolDefinitionFactory.read(ToolCode.APPROVAL_TASK_PARTICIPANTS_QUERY, definition.name(), definition.description(), definition.purpose(),
                oldInput, oldOutput, definition.requiredPermissions(), definition.ownershipPolicy(), definition.maxResultItems(), definition.maxResultBytes(), definition.timeoutMs()).schemaHash()).isEqualTo(OLD_HASH);
        jdbc.update("UPDATE agent_tool SET parameters_schema=?::jsonb,output_schema=?::jsonb,schema_hash=?,enabled=false WHERE tenant_id IS NULL AND code=?", oldInput.toString(), oldOutput.toString(), OLD_HASH, CODE);
        long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        jdbc.update("UPDATE agent_tenant_policy SET enabled=false,write_tools_enabled=false WHERE tenant_id=?", tenant);
        copyOverride(tenant);
        jdbc.update("UPDATE agent_tool SET description='Tenant custom description',max_result_items=10,required_permissions='[\"approval:act\",\"audit:read\"]'::jsonb WHERE tenant_id=? AND code=?", tenant, CODE);
        long customTenant = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Synthetic custom contract') RETURNING id", Long.class, "PAGING_" + UUID.randomUUID().toString().substring(0, 16));
        copyOverride(customTenant);
        String customHash = "sha256:" + "0".repeat(64);
        jdbc.update("UPDATE agent_tool SET schema_hash=? WHERE tenant_id=? AND code=?", customHash, customTenant, CODE);
        int before = jdbc.queryForObject("SELECT count(*) FROM agent_tool", Integer.class);
        String unrelated = jdbc.queryForObject("SELECT to_jsonb(t)::text FROM agent_tool t WHERE tenant_id IS NULL AND code='todo.query'", String.class);

        // Simulate a previous deployment only inside this verified, disposable database.
        assertThat(jdbc.update("DELETE FROM flyway_schema_history WHERE version='202611041130'")).isEqualTo(1);
        assertThat(flyway().info().pending()).hasSize(1);
        assertThat(flyway().migrate().migrationsExecuted).isEqualTo(1);
        assertContract(null); assertContract(tenant);
        assertThat(jdbc.queryForObject("SELECT enabled FROM agent_tool WHERE tenant_id IS NULL AND code=?", Boolean.class, CODE)).isFalse();
        assertThat(jdbc.queryForObject("SELECT enabled=false AND max_result_items=10 AND description='Tenant custom description' AND required_permissions='[\"approval:act\",\"audit:read\"]'::jsonb FROM agent_tool WHERE tenant_id=? AND code=?", Boolean.class, tenant, CODE)).isTrue();
        assertThat(jdbc.queryForObject("SELECT schema_hash FROM agent_tool WHERE tenant_id=? AND code=?", String.class, customTenant, CODE)).isEqualTo(customHash);
        assertThat(jdbc.queryForObject("SELECT enabled=false AND write_tools_enabled=false FROM agent_tenant_policy WHERE tenant_id=?", Boolean.class, tenant)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool", Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT to_jsonb(t)::text FROM agent_tool t WHERE tenant_id IS NULL AND code='todo.query'", String.class)).isEqualTo(unrelated);
        flyway().validate(); assertThat(flyway().info().pending()).isEmpty();
        assertThat(flyway().migrate().migrationsExecuted).isZero();
        flyway().validate(); assertThat(flyway().migrate().migrationsExecuted).isZero();
    }
    private void copyOverride(long tenant) {
        jdbc.update("""
                INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,schema_hash,
                    risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,confirmation_policy,
                    max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
                SELECT ?,code,name,description,handler_version,parameters_schema,output_schema,schema_hash,
                    risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,confirmation_policy,
                    max_result_items,max_result_bytes,timeout_ms,audit_level,false FROM agent_tool WHERE tenant_id IS NULL AND code=?
                """, tenant, CODE);
    }
    private void assertContract(Long tenant) throws Exception {
        String where = tenant == null ? "tenant_id IS NULL" : "tenant_id=" + tenant;
        var definition = catalog.find(CODE).orElseThrow();
        assertThat(jdbc.queryForObject("SELECT schema_hash FROM agent_tool WHERE code=? AND " + where, String.class, CODE)).isEqualTo(definition.schemaHash());
        assertThat(json.readTree(jdbc.queryForObject("SELECT parameters_schema::text FROM agent_tool WHERE code=? AND " + where, String.class, CODE))).isEqualTo(definition.inputSchema());
        assertThat(json.readTree(jdbc.queryForObject("SELECT output_schema::text FROM agent_tool WHERE code=? AND " + where, String.class, CODE))).isEqualTo(definition.outputSchema());
    }
    private Flyway flyway() {
        // Only this owned fixture replays a removed historical entry after newer migrations have run.
        return Flyway.configure().dataSource(dataSource).schemas(ENVIRONMENT.schema()).defaultSchema(ENVIRONMENT.schema()).outOfOrder(true).locations("classpath:db/migration").load();
    }
}
