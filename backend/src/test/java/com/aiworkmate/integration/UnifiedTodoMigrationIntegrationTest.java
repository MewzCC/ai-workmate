package com.aiworkmate.integration;

import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.registry.ToolDefinitionFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class UnifiedTodoMigrationIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT=new AgentIntegrationEnvironment();
    private static final String OLD="sha256:5587d07883805ecb5810979e984dc33044c68898a9e9aeed4d07c4f3a9793c69";
    @Override protected AgentIntegrationEnvironment environment(){return ENVIRONMENT;}
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource datasource;
    @Autowired ToolCatalog catalog;
    @Autowired ObjectMapper json;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){ENVIRONMENT.register(p);}
    @Test void upgradesRecognizedContractsAndPreservesDisabledPoliciesCustomLimitsAndUnknownHashes() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo(ENVIRONMENT.schema());
        var definition=catalog.find("todo.query").orElseThrow();
        assertThat(definition.schemaHash()).isEqualTo("sha256:2439f3d4f40632017737b60f9e821111762befa3a669be4e4779a84810f53671");
        assertContract(null);flyway().validate();assertThat(flyway().info().pending()).isEmpty();
        var input=(ObjectNode)definition.inputSchema().deepCopy();
        ((ObjectNode)input.path("properties")).remove("businessType");((ArrayNode)input.at("/properties/status/enum")).remove(0);
        var output=(ObjectNode)definition.outputSchema().deepCopy();output.putArray("required").add("items").add("total").add("page").add("size");
        ((ObjectNode)output.path("properties")).remove(List.of("businessType","status","from","to"));((ObjectNode)output.at("/properties/page")).remove("maximum");
        var item=(ObjectNode)output.at("/properties/items/items");item.putArray("required").add("id").add("applicationId").add("applicantName").add("leaveType").add("durationHalfDays").add("status").add("version").add("overdue");
        ((ObjectNode)item.path("properties")).remove(List.of("businessType","title","nodeName","detailAvailable"));
        assertThat(ToolDefinitionFactory.read(ToolCode.TODO_QUERY,definition.name(),definition.description(),definition.purpose(),input,output,definition.requiredPermissions(),definition.ownershipPolicy(),50,65536,15000).schemaHash()).isEqualTo(OLD);
        jdbc.update("UPDATE agent_tool SET parameters_schema=?::jsonb,output_schema=?::jsonb,schema_hash=?,enabled=false WHERE tenant_id IS NULL AND code='todo.query'",input.toString(),output.toString(),OLD);
        long tenant=jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'",Long.class);copy(tenant);
        jdbc.update("UPDATE agent_tool SET required_permissions='[\"todo:read\",\"audit:read\"]'::jsonb,max_result_items=10,description='Owned custom policy' WHERE tenant_id=? AND code='todo.query'",tenant);
        jdbc.update("UPDATE agent_tenant_policy SET enabled=false,write_tools_enabled=false WHERE tenant_id=?",tenant);
        long unknown=jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Owned custom hash') RETURNING id",Long.class,"UTC_"+UUID.randomUUID().toString().substring(0,16));copy(unknown);
        String custom="sha256:"+"0".repeat(64);jdbc.update("UPDATE agent_tool SET schema_hash=? WHERE tenant_id=? AND code='todo.query'",custom,unknown);
        int before=jdbc.queryForObject("SELECT count(*) FROM agent_tool",Integer.class);
        var replay=jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE version::numeric>=202611041330 ORDER BY installed_rank",String.class);
        assertThat(replay).contains("202611041330");assertThat(jdbc.update("DELETE FROM flyway_schema_history WHERE version::numeric>=202611041330")).isEqualTo(replay.size());
        assertThat(flyway().migrate().migrationsExecuted).isEqualTo(replay.size());assertContract(null);assertContract(tenant);
        assertThat(jdbc.queryForObject("SELECT enabled=false AND required_permissions='[\"todo:read\",\"audit:read\"]'::jsonb AND max_result_items=10 AND description='Owned custom policy' FROM agent_tool WHERE tenant_id=? AND code='todo.query'",Boolean.class,tenant)).isTrue();
        assertThat(jdbc.queryForObject("SELECT enabled FROM agent_tool WHERE tenant_id IS NULL AND code='todo.query'",Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT enabled=false AND write_tools_enabled=false FROM agent_tenant_policy WHERE tenant_id=?",Boolean.class,tenant)).isTrue();
        assertThat(jdbc.queryForObject("SELECT schema_hash FROM agent_tool WHERE tenant_id=? AND code='todo.query'",String.class,unknown)).isEqualTo(custom);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool",Integer.class)).isEqualTo(before);
        flyway().validate();assertThat(flyway().info().pending()).isEmpty();assertThat(flyway().migrate().migrationsExecuted).isZero();
        flyway().validate();assertThat(flyway().migrate().migrationsExecuted).isZero();
    }
    private void copy(long tenant){jdbc.update("INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled) SELECT ?,code,name,description,handler_version,parameters_schema,output_schema,schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,false FROM agent_tool WHERE tenant_id IS NULL AND code='todo.query'",tenant);}
    private void assertContract(Long tenant) throws Exception {String where=tenant==null?"tenant_id IS NULL":"tenant_id="+tenant;var definition=catalog.find("todo.query").orElseThrow();assertThat(jdbc.queryForObject("SELECT schema_hash FROM agent_tool WHERE code='todo.query' AND "+where,String.class)).isEqualTo(definition.schemaHash());assertThat(json.readTree(jdbc.queryForObject("SELECT output_schema::text FROM agent_tool WHERE code='todo.query' AND "+where,String.class))).isEqualTo(definition.outputSchema());}
    private Flyway flyway(){return Flyway.configure().dataSource(datasource).schemas(ENVIRONMENT.schema()).defaultSchema(ENVIRONMENT.schema()).locations("classpath:db/migration").load();}
}
