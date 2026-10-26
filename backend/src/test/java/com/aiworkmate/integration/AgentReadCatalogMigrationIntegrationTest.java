package com.aiworkmate.integration;

import com.aiworkmate.agent.registry.ToolCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Empty-db startup plus an exact data-only restoration of the preceding baseline, only in our owned DB. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "AGENT_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AgentReadCatalogMigrationIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final String LEGACY_VISITOR_HASH = "sha256:aa6f923d24734c45f3022026885641deb9b74abbe6e9061394234e5cc739a3aa";
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ToolCatalog catalog;
    @Autowired private ObjectMapper json;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        ENVIRONMENT.register(properties);
        properties.add("agent.enabled", () -> "false");
        properties.add("agent.execution-enabled", () -> "false");
        properties.add("agent.planning-enabled", () -> "false");
    }

    @Test void validatesEmptyAndExistingDatabaseUpgradesWithoutExpandingThePreviousRouteAudience() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(ENVIRONMENT.schema());
        assertCurrentContracts();
        flyway().validate();
        assertThat(flyway().info().pending()).isEmpty();

        // Undo only the new data migrations in this freshly owned DB, producing its exact old contract state.
        jdbc.update("DELETE FROM rbac_role_permission WHERE permission_code='route:approval-form'");
        jdbc.update("UPDATE rbac_route SET permission_code='access:manage' WHERE route_key='approval-form'");
        jdbc.update("DELETE FROM rbac_permission WHERE code='route:approval-form'");
        ObjectNode oldOutput = (ObjectNode) catalog.find("visitor.query").orElseThrow().outputSchema().deepCopy();
        ((ArrayNode) oldOutput.at("/properties/items/items/required")).insert(6,"expectedLeaveAt");
        jdbc.update("UPDATE agent_tool SET output_schema=?::jsonb,schema_hash=? WHERE code='visitor.query'", oldOutput.toString(), LEGACY_VISITOR_HASH);
        jdbc.update("UPDATE agent_tool SET required_permissions='[\"approval:mine:read\"]'::jsonb WHERE code IN ('approval.application.mine.query','approval.application.detail.query')");
        restoreLegacyGovernance("accessGovernance.query", "sha256:d622f57307336098c25b779333ff842a874adcbf1880ae687c08276d3c6bbc61", "roleCount", "rolesTruncated");
        restoreLegacyGovernance("dataPermission.query", "sha256:858c22c5e8947762eda39892caf2fbf962282c21d94945491daf34ebde3f6c21", "policyCount", "policiesTruncated");
        restoreLegacyGovernance("aiPermission.query", "sha256:270d6da876eea0376fddf296219842f25f2bdc594ec61023ac365006cfc7ae87", "toolCount", "roleCount", "toolsTruncated", "rolesTruncated");
        var oldAttendance = (ObjectNode)catalog.find("attendance.query").orElseThrow().outputSchema().deepCopy();
        ((ObjectNode)oldAttendance.at("/properties/reissues/items/properties")).remove("version");
        ((ObjectNode)oldAttendance.at("/properties/settings/properties")).remove("version");
        jdbc.update("UPDATE agent_tool SET output_schema=?::jsonb,schema_hash=? WHERE code='attendance.query'",oldAttendance.toString(),"sha256:4ff357d3836da753f0c32d3774ea04ba3cf83d1c6b8708de548e8ff711348d60");
        assertThat(jdbc.update("DELETE FROM flyway_schema_history WHERE version IN ('202610260915','202610260916','202610260917','202610260918','202610260919','202610260920')")).isEqualTo(6);

        long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0,12);
        String manager = role(tenant, "MGR_"+suffix, "access:manage");
        String starter = role(tenant, "START_"+suffix, "route:approval-start");
        String member = role(tenant, "MEMBER_"+suffix, null);
        long direct = user(tenant, manager), assigned = user(tenant, member), unassigned = user(tenant, starter);
        jdbc.update("INSERT INTO user_role(tenant_id,user_id,role_code) VALUES (?,?,?)", tenant, assigned, manager);
        long otherTenant = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'synthetic other tenant') RETURNING id", Long.class, "UPGRADE_"+suffix);
        String otherManager = role(otherTenant, "OTHER_"+suffix, "access:manage");
        long otherUser = user(otherTenant, otherManager);

        assertThat(flyway().info().pending()).hasSize(6);
        assertThat(flyway().migrate().migrationsExecuted).isEqualTo(6);
        assertCurrentContracts();
        assertThat(granted(tenant, manager)).isTrue();
        assertThat(granted(tenant, starter)).isFalse();
        assertThat(granted(otherTenant, otherManager)).isFalse();
        assertThat(version(direct)).isEqualTo(2);
        assertThat(version(assigned)).isEqualTo(2);
        assertThat(version(unassigned)).isEqualTo(1);
        assertThat(version(otherUser)).isEqualTo(1);

        // A new Flyway instance validates checksums and executes nothing after the upgrade.
        flyway().validate();
        assertThat(flyway().migrate().migrationsExecuted).isZero();
        assertThat(flyway().info().pending()).isEmpty();
        assertThat(version(direct)).isEqualTo(2);
        assertThat(version(assigned)).isEqualTo(2);
    }

    private void assertCurrentContracts() {
        assertThat(jdbc.queryForObject("SELECT permission_code FROM rbac_route WHERE route_key='approval-form'", String.class)).isEqualTo("route:approval-form");
        assertThat(jdbc.queryForObject("SELECT schema_hash FROM agent_tool WHERE tenant_id IS NULL AND code='visitor.query'", String.class))
                .isEqualTo(catalog.find("visitor.query").orElseThrow().schemaHash());
        for (String code : Arrays.asList("approval.application.mine.query","approval.application.detail.query")) {
            assertThat(jdbc.queryForObject("SELECT required_permissions='[\"approval:mine:read\"]'::jsonb FROM agent_tool WHERE tenant_id IS NULL AND code=?", Boolean.class, code)).isTrue();
        }
        for (String code : Arrays.asList("accessGovernance.query","dataPermission.query","aiPermission.query","attendance.query")) {
            var definition = catalog.find(code).orElseThrow();
            assertThat(jdbc.queryForObject("SELECT schema_hash FROM agent_tool WHERE tenant_id IS NULL AND code=?", String.class, code)).isEqualTo(definition.schemaHash());
            try { assertThat(json.readTree(jdbc.queryForObject("SELECT output_schema::text FROM agent_tool WHERE tenant_id IS NULL AND code=?", String.class, code))).isEqualTo(definition.outputSchema()); }
            catch (Exception e) { throw new AssertionError("governance schema mismatch: " + code, e); }
        }
    }

    private void restoreLegacyGovernance(String code, String oldHash, String... metadata) {
        ObjectNode schema = (ObjectNode) catalog.find(code).orElseThrow().outputSchema().deepCopy();
        var fields = java.util.Set.of(metadata);
        ((ObjectNode)schema.path("properties")).remove(fields);
        var required = schema.putArray("required");
        catalog.find(code).orElseThrow().outputSchema().path("required").forEach(field -> { if (!fields.contains(field.asText())) required.add(field); });
        jdbc.update("UPDATE agent_tool SET output_schema=?::jsonb,schema_hash=? WHERE code=?",schema.toString(),oldHash,code);
    }

    private Flyway flyway() { return Flyway.configure().dataSource(dataSource).schemas(ENVIRONMENT.schema()).defaultSchema(ENVIRONMENT.schema()).locations("classpath:db/migration").load(); }
    private String role(long tenant, String code, String permission) {
        jdbc.update("INSERT INTO rbac_role(tenant_id,code,name,description,builtin) VALUES (?,?,?,'owned migration fixture',false)", tenant, code, code);
        if (permission != null) jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,?)", tenant, code, permission);
        return code;
    }
    private long user(long tenant, String role) {
        String name = "migration-"+UUID.randomUUID();
        return jdbc.queryForObject("INSERT INTO app_user(tenant_id,username,password,role,permission_version) VALUES (?,?,'synthetic fixture',?,1) RETURNING id", Long.class, tenant, name, role);
    }
    private boolean granted(long tenant, String role) { return jdbc.queryForObject("SELECT count(*) FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='route:approval-form'", Integer.class, tenant, role)>0; }
    private long version(long user) { return jdbc.queryForObject("SELECT permission_version FROM app_user WHERE id=?", Long.class, user); }
}
