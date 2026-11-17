package com.aiworkmate.integration;

import com.aiworkmate.agent.registry.ToolCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApprovalApplicationTableIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENV = new AgentIntegrationEnvironment();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment() { return ENV; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired ToolCatalog catalog;
    @Autowired org.flywaydb.core.Flyway flyway;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor owner, colleague;
    private String formKey;
    private long tenant;

    private java.net.http.HttpResponse<String> send(AgentHttpTestFixture.Actor actor, String method,
            String path, String body, String key) throws Exception {
        return java.net.http.HttpClient.newHttpClient().send(http.request(method, path, actor, key, body).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        AgentBrowserFixture.registerLocal(properties, ENV, AgentBrowserFixture.reserveUiPort(), SECRET);
    }

    @BeforeEach void setup() throws Exception {
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        http = new AgentHttpTestFixture(jdbc, json, passwords, port, "en-US");
        owner = http.createAndLogin(tenant, List.of("route:approval-start", "approval:create", "approval:submit", "approval:mine:read"));
        colleague = http.createAndLogin(tenant, List.of("route:approval-start", "approval:create", "approval:mine:read"));
        var reviewer = http.createAndLogin(tenant, List.of("todo:read", "approval:act"));
        jdbc.update("UPDATE app_user SET approver_user_id=? WHERE id=?", reviewer.id(), owner.id());
        formKey = "table_" + UUID.randomUUID().toString().replace("-", "");
        String schema = """
                {"fields":[{"name":"items","label":"Historic items","type":"table","required":true,"columns":[
                {"name":"amount","label":"Historic amount","type":"money","required":true,"min":0},
                {"name":"note","label":"Note","type":"text"}]}]}
                """;
        long form = jdbc.queryForObject("INSERT INTO approval_form(tenant_id,form_key,form_name,schema_json,status,created_by) VALUES (?,?,?,?,'ENABLED',?) RETURNING id",
                Long.class, tenant, formKey, formKey, schema, owner.id());
        jdbc.update("INSERT INTO approval_process(tenant_id,process_key,process_name,form_id,node_json,status) VALUES (?,?,?,?,'[{\"nodeName\":\"Review\",\"approveType\":\"DIRECT_MANAGER\"}]','ENABLED')",
                tenant, formKey, formKey, form);
    }

    private long create(String rows) throws Exception {
        var response = send(owner, "POST", "/api/approval-applications/drafts",
                "{\"formKey\":\"" + formKey + "\",\"formData\":{\"items\":" + rows + "}}", null);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).at("/data/id").asLong();
    }

    @Test void freezesRowsAndColumnLabelsThroughProtectedHttpWithoutLosingMoney() throws Exception {
        long application = create("[{\"amount\":9007199254740993.01,\"note\":\"First\"}]");
        var submit = send(owner, "POST", "/api/approval-applications/" + application + "/submit", "{\"version\":0}", null);
        assertThat(submit.statusCode()).isEqualTo(200);
        jdbc.update("UPDATE approval_form SET schema_json=replace(schema_json,'Historic amount','Changed configuration') WHERE form_key=? AND tenant_id=?", formKey, tenant);
        var detail = send(owner, "GET", "/api/approval-applications/mine/" + application + "/fields", null, null);
        assertThat(detail.statusCode()).isEqualTo(200);
        var result = json.readTree(detail.body()).path("data");
        assertThat(result.path("schemaSource").asText()).isEqualTo("SNAPSHOT");
        assertThat(result.at("/fields/0/table/columns/0/label").asText()).isEqualTo("Historic amount");
        assertThat(result.at("/fields/0/table/rows/0/0").asText()).isEqualTo("9007199254740993.01");
        assertThat(send(colleague, "GET", "/api/approval-applications/mine/" + application + "/fields", null, null).statusCode()).isIn(403, 404);
    }

    @Test void allowsIncompleteDraftButRejectsRequiredCellsUnknownColumnsAndStaleVersions() throws Exception {
        long application = create("[{}]");
        String path = "/api/approval-applications/" + application;
        assertThat(send(owner, "POST", path + "/submit", "{\"version\":0}", null).statusCode()).isEqualTo(400);
        assertThat(send(owner, "PUT", path + "/draft", "{\"version\":0,\"formData\":{\"items\":[{\"amount\":\"1\",\"amount\":\"2\"}]}}", null).statusCode()).isEqualTo(400);
        assertThat(send(owner, "PUT", path + "/draft", "{\"version\":0,\"formData\":{\"items\":[{\"unknown\":\"x\"}]}}", null).statusCode()).isEqualTo(400);
        assertThat(send(owner, "PUT", path + "/draft", "{\"version\":0,\"formData\":{\"items\":[{\"amount\":\"0.10\"}]}}", null).statusCode()).isEqualTo(200);
        assertThat(send(owner, "PUT", path + "/draft", "{\"version\":0,\"formData\":{\"items\":[]}}", null).statusCode()).isEqualTo(409);
    }

    @Test void persistsNumericExponentAsAnExactEditableDecimalString() throws Exception {
        long application = create("[{\"amount\":1e3}]");
        var stored = json.readTree(jdbc.queryForObject("SELECT data_json FROM approval_application WHERE id=?", String.class, application));
        assertThat(stored.at("/items/0/amount").isTextual()).isTrue();
        assertThat(stored.at("/items/0/amount").asText()).isEqualTo("1000");
    }

    @Test void migratedTableContractsMatchCodePreserveActivationAndRestartHasNoPendingMigrations() throws Exception {
        for (String code : List.of("approval.application.createDraft", "approval.application.updateDraft", "approval.application.apply",
                "approval.application.detail.query", "approval.genericTask.detail.query", "approval.form.definition.query", "approval.application.template.query")) {
            var expected = catalog.find(code).orElseThrow();
            var stored = jdbc.queryForMap("SELECT schema_hash,parameters_schema::text,output_schema::text,enabled FROM agent_tool WHERE tenant_id IS NULL AND code=?", code);
            assertThat(stored.get("schema_hash")).as(code).isEqualTo(expected.schemaHash());
            assertThat(json.readTree((String) stored.get("parameters_schema"))).isEqualTo(expected.inputSchema());
            assertThat(json.readTree((String) stored.get("output_schema"))).isEqualTo(expected.outputSchema());
            if (code.equals("approval.application.apply")) assertThat(stored.get("enabled")).isEqualTo(false);
            // Historical draft tools were already seeded active; extending their schema must not change that setting.
            if (code.equals("approval.application.createDraft") || code.equals("approval.application.updateDraft")) {
                assertThat(stored.get("enabled")).isEqualTo(true);
            }
        }
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test void incrementalUpgradePreservesActivationAndDoesNotOverwriteUnknownContracts() {
        String unknownHash = "sha256:" + "0".repeat(64);
        String ownDetail = "approval.application.detail.query";
        var currentDetail = catalog.find(ownDetail).orElseThrow();
        try {
            jdbc.update("UPDATE agent_tool SET parameters_schema=?::jsonb,schema_hash=?,enabled=false WHERE tenant_id IS NULL AND code='approval.application.createDraft'",
                    com.aiworkmate.agent.registry.AgentApprovalApplicationWriteToolDefinitions.PRE_TABLE_CREATE_DRAFT_INPUT_SCHEMA,
                    "sha256:0b91ec92030a3bdb99baac22dce2b1e7c3a239829740fe9ad174b24ecf340424");
            jdbc.update("UPDATE agent_tool SET parameters_schema=?::jsonb,schema_hash=?,enabled=true WHERE tenant_id IS NULL AND code='approval.application.updateDraft'",
                    com.aiworkmate.agent.registry.AgentApprovalApplicationWriteToolDefinitions.PRE_TABLE_UPDATE_DRAFT_INPUT_SCHEMA,
                    "sha256:a5c42c9496a21a5168c36ccda217610628fe07528ea042342fa483368e445290");
            jdbc.update("UPDATE agent_tool SET parameters_schema=?::jsonb,schema_hash=?,enabled=false WHERE tenant_id IS NULL AND code='approval.application.apply'",
                    com.aiworkmate.agent.registry.AgentApprovalApplicationWriteToolDefinitions.PRE_TABLE_CREATE_DRAFT_INPUT_SCHEMA,
                    "sha256:c614bce4890b0f2b8ae04b4b75000c73f4184fe0be2133a9fc84b4f341e5db1e");
            jdbc.update("UPDATE agent_tool SET schema_hash=? WHERE tenant_id IS NULL AND code=?", unknownHash, ownDetail);
            assertThat(jdbc.update("DELETE FROM flyway_schema_history WHERE version='202611121700'")).isEqualTo(1);
            // Replaying this historical migration in the owned fixture must also work
            // after later migrations have been added; production ordering stays strict.
            var replay = Flyway.configure().configuration(flyway.getConfiguration()).outOfOrder(true).load();
            assertThat(replay.migrate().migrationsExecuted).isEqualTo(1);
            for (String code : List.of("approval.application.createDraft", "approval.application.updateDraft", "approval.application.apply")) {
                assertThat(jdbc.queryForObject("SELECT schema_hash FROM agent_tool WHERE tenant_id IS NULL AND code=?", String.class, code))
                        .isEqualTo(catalog.find(code).orElseThrow().schemaHash());
                assertThat(jdbc.queryForObject("SELECT enabled FROM agent_tool WHERE tenant_id IS NULL AND code=?", Boolean.class, code))
                        .isEqualTo(code.equals("approval.application.updateDraft"));
            }
            assertThat(jdbc.queryForObject("SELECT schema_hash FROM agent_tool WHERE tenant_id IS NULL AND code=?", String.class, ownDetail))
                    .isEqualTo(unknownHash);
            flyway.validate();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        } finally {
            jdbc.update("UPDATE agent_tool SET schema_hash=? WHERE tenant_id IS NULL AND code=?", currentDetail.schemaHash(), ownDetail);
            jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code='approval.application.createDraft'");
        }
    }
}
