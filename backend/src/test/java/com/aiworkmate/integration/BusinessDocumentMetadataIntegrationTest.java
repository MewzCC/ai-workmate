package com.aiworkmate.integration;

import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.aiworkmate.service.FileParserService;
import com.aiworkmate.service.ObjectStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BusinessDocumentMetadataIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENV = new AgentIntegrationEnvironment();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    private static final AtomicLong IDS = new AtomicLong(9007199254740993L);
    @Override protected AgentIntegrationEnvironment environment() { return ENV; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired ToolCatalog catalog;
    @Autowired AgentHashing hashing;
    @Autowired AgentTaskWorker worker;
    @Autowired org.flywaydb.core.Flyway flyway;
    @SpyBean ObjectStorageService storage;
    @SpyBean FileParserService parser;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor owner, other, outsider;
    private long tenant, foreignTenant, ownUsage, otherUsage, foreignUsage;
    private final Map<ToolCode, List<String>> ownIds = new HashMap<>();
    static Stream<ToolCode> tools() { return Stream.of(ToolCode.HR_EMPLOYEE_DOCUMENTS_QUERY, ToolCode.SEAL_DOCUMENTS_QUERY); }

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        AgentBrowserFixture.registerLocal(properties, ENV, AgentBrowserFixture.reserveUiPort(), SECRET);
        properties.add("agent.enabled", () -> "true");
        properties.add("agent.execution-enabled", () -> "true");
        properties.add("agent.write-tools-enabled", () -> "false");
        properties.add("agent.worker.poll-delay-ms", () -> "3600000");
        properties.add("agent.worker.heartbeat-delay-ms", () -> "3600000");
    }

    @BeforeEach void setup() throws Exception {
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        foreignTenant = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Metadata foreign tenant') RETURNING id", Long.class,
                "META_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        http = new AgentHttpTestFixture(jdbc, json, passwords, port, "en-US");
        var permissions = List.of("route:employee-files", "route:seal-usage", "route:visitor-booking", "visitor:read:self", "hr:read", "seal:register",
                "agent:tool:hr.employee.documents.query", "agent:tool:seal.documents.query");
        owner = http.createAndLogin(tenant, permissions);
        other = http.createAndLogin(tenant, permissions);
        outsider = http.createAndLogin(foreignTenant, permissions);
        ownUsage = usage(tenant, owner.id()); otherUsage = usage(tenant, other.id()); foreignUsage = usage(foreignTenant, outsider.id());
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=false WHERE tenant_id=?", tenant);
        for (var code : tools().toList()) {
            var ids = new ArrayList<String>();
            for (int index = 0; index < 25; index++) ids.add(document(code, tenant, resource(code, 0), owner.id(), "Own document " + index, index));
            ownIds.put(code, List.copyOf(ids));
            document(code, tenant, resource(code, 1), other.id(), "PRIVATE-other", 0);
            document(code, foreignTenant, resource(code, 2), outsider.id(), "PRIVATE-foreign", 0);
        }
        clearInvocations(storage, parser);
    }

    @AfterEach void restore() {
        jdbc.execute("DROP TRIGGER IF EXISTS metadata_audit_fault ON agent_tool_invocation");
        jdbc.execute("DROP FUNCTION IF EXISTS metadata_audit_fault()");
        jdbc.update("UPDATE agent_tool SET enabled=false WHERE tenant_id IS NULL AND code IN ('hr.employee.documents.query','seal.documents.query')");
    }

    private long usage(long tenantId, long applicant) {
        return jdbc.queryForObject("INSERT INTO seal_usage(tenant_id,applicant_user_id,seal_type,document_title,usage_reason,status,submitted_at) VALUES (?,?,'OFFICIAL','Metadata fixture','Metadata fixture','APPROVED',CURRENT_TIMESTAMP) RETURNING id", Long.class, tenantId, applicant);
    }

    private long resource(ToolCode code, int scope) {
        return code == ToolCode.HR_EMPLOYEE_DOCUMENTS_QUERY
                ? scope == 0 ? owner.id() : scope == 1 ? other.id() : outsider.id()
                : scope == 0 ? ownUsage : scope == 1 ? otherUsage : foreignUsage;
    }

    private String document(ToolCode code, long tenantId, long resource, long uploader, String name, int index) {
        long id = IDS.getAndIncrement();
        String key = "private-storage/" + UUID.randomUUID();
        var created = LocalDateTime.of(2026, 11, 13, 12, 0).plusSeconds(index);
        if (code == ToolCode.HR_EMPLOYEE_DOCUMENTS_QUERY) {
            jdbc.update("INSERT INTO employee_document(id,tenant_id,employee_user_id,document_type,display_name,storage_key,mime_type,file_size,uploaded_by_user_id,created_at) VALUES (?,?,?,'PROFILE',?,?,'text/plain',100,?,?)",
                    id, tenantId, resource, name, key, uploader, created);
        } else {
            jdbc.update("INSERT INTO seal_usage_document(id,tenant_id,seal_usage_id,display_name,storage_key,mime_type,file_size,uploaded_by_user_id,created_at) VALUES (?,?,?,?,?,'text/plain',100,?,?)",
                    id, tenantId, resource, name, key, uploader, created);
        }
        return Long.toString(id);
    }

    private FrozenReadTaskFixture.Task plan(ToolCode code, long resource, int page, int size, boolean queued, String sourcePage) {
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code=?", code.code());
        var args = json.createObjectNode().put(code == ToolCode.HR_EMPLOYEE_DOCUMENTS_QUERY ? "employeeId" : "usageId", resource).put("page", page).put("size", size);
        return new FrozenReadTaskFixture(jdbc, json, hashing, catalog).create(tenant, owner.id(), sourcePage, code.code(), args,
                queued ? FrozenReadTaskFixture.State.QUEUED : FrozenReadTaskFixture.State.PLAN_READY);
    }

    private String page(ToolCode code) { return code == ToolCode.HR_EMPLOYEE_DOCUMENTS_QUERY ? "employee-files" : "seal-usage"; }

    private JsonNode run(FrozenReadTaskFixture.Task task, boolean queued, String expected) throws Exception {
        if (!queued) {
            var body = json.createObjectNode().put("planVersion", 1).put("planHash", task.planHash()).toString();
            var response = AgentHttpTestFixture.client().send(http.request("POST", "/api/ai/tasks/" + task.number() + "/execute", owner,
                    UUID.randomUUID().toString(), body).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(202);
        }
        await().atMost(Duration.ofSeconds(25)).until(() -> {
            worker.poll();
            return !List.of("QUEUED", "RUNNING").contains(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, task.id()));
        });
        assertThat(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, task.id()))
                .as("metadata error=%s", jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?", String.class, task.id())).isEqualTo(expected);
        var response = AgentHttpTestFixture.client().send(http.request("GET", "/api/ai/tasks/" + task.number(), owner, null, null).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).at("/data/steps/0");
    }

    @ParameterizedTest @MethodSource("tools")
    void realHttpAndGatewayReturnTwoBoundedPagesWithoutReadingAnyFile(ToolCode code) throws Exception {
        var first = run(plan(code, resource(code, 0), 1, 20, false, page(code)), false, "SUCCEEDED");
        var result = first.path("result");
        assertThat(result.path("total").asInt()).isEqualTo(25);
        assertThat(result.path("items")).hasSize(20);
        assertThat(result.at("/items/0/documentId").asText()).isEqualTo(ownIds.get(code).get(24));
        assertThat(first.path("resultSummary").asText()).contains("Own document 24", "100");
        var second = run(plan(code, resource(code, 0), 2, 20, false, page(code)), false, "SUCCEEDED").path("result");
        assertThat(second.path("items")).hasSize(5);
        assertThat(second.at("/items/0/documentId").asText()).isEqualTo(ownIds.get(code).get(4));
        assertThat(result.toString() + second).doesNotContain("private-storage", "storageKey", "contentUrl", "uploadedBy", "PRIVATE-other", "PRIVATE-foreign");
        verifyNoInteractions(storage, parser);
    }

    @ParameterizedTest @MethodSource("tools")
    void resourceOwnershipAndTenantBoundariesRemainClosed(ToolCode code) throws Exception {
        for (int scope : List.of(1, 2)) {
            var task = plan(code, resource(code, scope), 1, 20, true, page(code));
            assertThat(run(task, true, "FAILED").hasNonNull("result")).isFalse();
            assertThat(jdbc.queryForObject("SELECT result IS NULL FROM agent_task_step WHERE task_id=?", Boolean.class, task.id())).isTrue();
        }
        verifyNoInteractions(storage, parser);
    }

    @ParameterizedTest @MethodSource("tools")
    void anEmptyResourceSucceedsButAnUnknownResourceDoesNotMasqueradeAsEmpty(ToolCode code) throws Exception {
        if (code == ToolCode.HR_EMPLOYEE_DOCUMENTS_QUERY) {
            jdbc.update("DELETE FROM employee_document WHERE tenant_id=? AND employee_user_id=?", tenant, owner.id());
        } else {
            jdbc.update("DELETE FROM seal_usage_document WHERE tenant_id=? AND seal_usage_id=?", tenant, ownUsage);
        }
        var empty = run(plan(code, resource(code, 0), 1, 20, false, page(code)), false, "SUCCEEDED");
        assertThat(empty.at("/result/items")).isEmpty();
        assertThat(empty.at("/result/total").asInt()).isZero();
        assertThat(empty.path("resultSummary").asText()).contains("No visible document metadata");
        var missing = run(plan(code, Long.MAX_VALUE, 1, 20, true, page(code)), true, "FAILED");
        assertThat(missing.hasNonNull("result")).isFalse();
        verifyNoInteractions(storage, parser);
    }

    @ParameterizedTest @MethodSource("tools")
    void revocationAndCrossPageUseAreBlockedBeforeHandlerInvocation(ToolCode code) throws Exception {
        var wrongPage = plan(code, resource(code, 0), 1, 20, true, "visitor-booking");
        run(wrongPage, true, "FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked", Long.class, wrongPage.id())).isZero();
        var revoked = plan(code, resource(code, 0), 1, 20, true, page(code));
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code=?", tenant, owner.role(),
                code == ToolCode.HR_EMPLOYEE_DOCUMENTS_QUERY ? "hr:read" : "seal:register");
        run(revoked, true, "FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked", Long.class, revoked.id())).isZero();
        verifyNoInteractions(storage, parser);
    }

    @ParameterizedTest @MethodSource("tools")
    void auditFailureDoesNotInvokeTheMetadataHandler(ToolCode code) throws Exception {
        var task = plan(code, resource(code, 0), 1, 20, true, page(code));
        jdbc.execute("CREATE FUNCTION metadata_audit_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic metadata audit fault'; END $$");
        jdbc.execute("CREATE TRIGGER metadata_audit_fault BEFORE INSERT ON agent_tool_invocation FOR EACH ROW EXECUTE FUNCTION metadata_audit_fault()");
        run(task, true, "FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked", Long.class, task.id())).isZero();
        verifyNoInteractions(storage, parser);
    }

    @Test void migrationSeedsClosedMatchingContractsAndDoesNotEnableTheTools() throws Exception {
        for (var code : tools().toList()) {
            var definition = catalog.find(code.code()).orElseThrow();
            var row = jdbc.queryForMap("SELECT schema_hash,parameters_schema::text,output_schema::text,enabled FROM agent_tool WHERE tenant_id IS NULL AND code=?", code.code());
            assertThat(row.get("schema_hash")).isEqualTo(definition.schemaHash());
            assertThat(json.readTree((String) row.get("parameters_schema"))).isEqualTo(definition.inputSchema());
            assertThat(json.readTree((String) row.get("output_schema"))).isEqualTo(definition.outputSchema());
            assertThat(row.get("enabled")).isEqualTo(false);
        }
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test void incrementalUpgradePreservesExistingConfigurationAndOnlyGrantsEligibleRoles() {
        String employee = ToolCode.HR_EMPLOYEE_DOCUMENTS_QUERY.code(), seal = ToolCode.SEAL_DOCUMENTS_QUERY.code();
        String unknownHash = "sha256:" + "0".repeat(64);
        var original = catalog.find(employee).orElseThrow();
        var roles = new HashMap<ToolCode, String>();
        String noPermission = "META_NONE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        jdbc.update("INSERT INTO rbac_role(tenant_id,code,name,description,builtin) VALUES (?,?,'Metadata unprivileged role','Owned migration fixture',false)", tenant, noPermission);
        for (var code : tools().toList()) {
            String role = "META_" + UUID.randomUUID().toString().replace("-", "");
            jdbc.update("INSERT INTO rbac_role(tenant_id,code,name,description,builtin) VALUES (?,?,'Metadata existing role','Owned migration fixture',false)", tenant, role);
            jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,?)", tenant, role,
                    code == ToolCode.HR_EMPLOYEE_DOCUMENTS_QUERY ? "hr:read" : "seal:register");
            roles.put(code, role);
        }
        try {
            jdbc.update("INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled) SELECT ?,code,'Owned tenant override',description,handler_version,parameters_schema,output_schema,?,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,true FROM agent_tool WHERE tenant_id IS NULL AND code=?",
                    foreignTenant, unknownHash, employee);
            jdbc.update("UPDATE agent_tool SET enabled=true,name='Owned platform override',schema_hash=? WHERE tenant_id IS NULL AND code=?", unknownHash, employee);
            jdbc.update("DELETE FROM agent_tool WHERE tenant_id IS NULL AND code=?", seal);
            assertThat(jdbc.update("DELETE FROM flyway_schema_history WHERE version='202611131300'")).isEqualTo(1);
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT name FROM agent_tool WHERE tenant_id IS NULL AND code=?", String.class, employee)).isEqualTo("Owned platform override");
            assertThat(jdbc.queryForObject("SELECT schema_hash FROM agent_tool WHERE tenant_id IS NULL AND code=?", String.class, employee)).isEqualTo(unknownHash);
            assertThat(jdbc.queryForObject("SELECT enabled FROM agent_tool WHERE tenant_id IS NULL AND code=?", Boolean.class, employee)).isTrue();
            assertThat(jdbc.queryForObject("SELECT name FROM agent_tool WHERE tenant_id=? AND code=?", String.class, foreignTenant, employee)).isEqualTo("Owned tenant override");
            assertThat(jdbc.queryForObject("SELECT enabled FROM agent_tool WHERE tenant_id IS NULL AND code=?", Boolean.class, seal)).isFalse();
            for (var code : tools().toList()) {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code=?", Integer.class,
                        tenant, roles.get(code), "agent:tool:" + code.code())).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code=?", Integer.class,
                        tenant, noPermission, "agent:tool:" + code.code())).isZero();
            }
            flyway.validate();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        } finally {
            jdbc.update("UPDATE agent_tool SET name=?,schema_hash=?,enabled=false WHERE tenant_id IS NULL AND code=?", original.name(), original.schemaHash(), employee);
            jdbc.update("DELETE FROM agent_tool WHERE tenant_id=? AND code=?", foreignTenant, employee);
        }
    }
}
