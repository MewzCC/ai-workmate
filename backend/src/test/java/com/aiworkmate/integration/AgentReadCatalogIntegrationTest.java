package com.aiworkmate.integration;

import com.aiworkmate.agent.capability.PageCapabilityCatalog;
import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.task.AgentTaskApiService;
import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.UserAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Whole read catalog through the real Worker/Gateway; frozen plans intentionally omit the LLM. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "AGENT_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentReadCatalogIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private ToolCatalog catalog;
    @Autowired private PageCapabilityCatalog pages;
    @Autowired private AgentHashing hashing;
    @Autowired private AgentTaskWorker worker;
    @Autowired private AgentTaskApiService api;
    @Autowired private UserAccessService access;
    @Autowired private AgentRuntimeProperties runtime;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        ENVIRONMENT.register(properties);
        properties.add("agent.enabled", () -> "true");
        properties.add("agent.execution-enabled", () -> "true");
        properties.add("agent.worker.poll-delay-ms", () -> "3600000");
        properties.add("agent.worker.heartbeat-delay-ms", () -> "3600000");
    }

    static Stream<ToolCode> readTools() { return Arrays.stream(ToolCode.values()).filter(code -> !code.isWrite()); }

    Stream<Arguments> enabledPages() {
        var routes = jdbc.queryForList("SELECT route_key,component_key FROM rbac_route WHERE route_type='PAGE' AND enabled ORDER BY route_key");
        assertThat(routes).hasSize(42);
        return routes.stream().map(route -> {
            String page = (String)route.get("route_key");
            var capability = pages.find(page).orElseThrow();
            assertThat(capability.componentKey()).as(page).isEqualTo(route.get("component_key"));
            assertThat(capability.readTools()).as(page).isNotEmpty();
            String tool = capability.readTools().stream().map(reference -> reference.toolCode()).sorted().findFirst().orElseThrow();
            return Arguments.of(page,ToolCode.fromCode(tool));
        });
    }

    @ParameterizedTest(name = "page source {0}: {1}")
    @MethodSource("enabledPages")
    void everyEnabledPageCanExecuteItsDeclaredRead(String page, ToolCode code) throws Exception {
        executeRead(code,page);
    }

    @Test void enabledPageRoutesUseTheCanonicalPermissionRequiredByTheirCapability() {
        assertThat(jdbc.queryForList("SELECT route_key FROM rbac_route WHERE route_type='PAGE' AND enabled AND permission_code<>'route:' || route_key", String.class)).isEmpty();
    }

    @Test void theReadMatrixMatchesTheCodeOwnedCatalogAndKeepsWritesDisabled() {
        assertThat(readTools().map(ToolCode::code).toList()).hasSize(41)
                .containsExactlyInAnyOrderElementsOf(catalog.all().stream().filter(tool -> !ToolCode.fromCode(tool.code()).isWrite()).map(tool -> tool.code()).toList());
        assertThat(runtime.isWriteToolsEnabled()).isFalse();
    }

    @ParameterizedTest(name = "real read {0}")
    @MethodSource("readTools")
    void everyReadToolExecutesItsRealDomainQuery(ToolCode code) throws Exception {
        executeRead(code,null);
    }

    private void executeRead(ToolCode code, String sourcePage) throws Exception {
        var actor = actor(code,sourcePage);
        var fixture = new AgentReadCatalogFixture(jdbc, json, actor.tenant(), actor.user(), actor.label());
        var input = sourcePage == null ? fixture.prepare(code) : fixture.prepareForPage(code,sourcePage);
        var task = frozen(actor, code, input.arguments());
        String terminal = run(task);
        assertThat(terminal).as("%s error=%s", code.code(), jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?", String.class, task.id())).isEqualTo("SUCCEEDED");
        var resolved = access.resolveActiveUser(actor.user());
        var detail = api.detail(new AuthenticatedUser(resolved.userId(), resolved.username(), resolved.tenantId(), resolved.role(),
                resolved.roles(), resolved.permissions(), resolved.dataScopes(), resolved.permissionVersion()), task.number());
        var result = detail.steps().get(0).result();
        assertThat(result).isNotNull();
        assertThat(result.isObject()).isTrue();
        if (input.evidence() != null) assertThat(result.toString()).as(code.code()).contains(input.evidence());
        if (code == ToolCode.KNOWLEDGE_SEARCH) assertThat(result.path("items")).isEmpty();
        if (code == ToolCode.AGENT_TASK_MINE_QUERY) assertThat(result.toString()).contains(task.number());
        assertThat(detail.steps().get(0).resultSummary()).as("readable summary: " + code.code()).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked AND decision='ALLOW' AND outcome='SUCCEEDED'", Long.class, task.id())).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT event_type FROM agent_task_event WHERE task_id=? ORDER BY id", String.class, task.id()))
                .containsSubsequence("step-started", "step-completed", "task-completed");
        assertThat(jdbc.queryForObject("SELECT tool_call_count FROM agent_task WHERE id=? AND worker_id IS NULL AND lease_token_hash IS NULL", Integer.class, task.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT write_tools_enabled FROM agent_tenant_policy WHERE tenant_id=?", Boolean.class, actor.tenant())).isFalse();
    }

    @ParameterizedTest(name = "revoked tool permission {0}")
    @MethodSource("readTools")
    void everyReadToolRechecksItsOwnPermissionBeforeCallingTheHandler(ToolCode code) {
        var actor = actor(code);
        var input = new AgentReadCatalogFixture(jdbc, json, actor.tenant(), actor.user(), actor.label()).prepare(code);
        var task = frozen(actor, code, input.arguments());
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code=?", actor.tenant(), actor.role(), "agent:tool:" + code.code());
        assertDenied(task, "REALTIME_PERMISSION_DENIED");
    }

    @ParameterizedTest(name = "forged tenant argument {0}")
    @MethodSource("readTools")
    void noReadSchemaAcceptsCallerSuppliedTenantAuthority(ToolCode code) {
        var actor = actor(code);
        var input = new AgentReadCatalogFixture(jdbc, json, actor.tenant(), actor.user(), actor.label()).prepare(code);
        input.arguments().put("tenantId", actor.tenant() + 1);
        var task = frozen(actor, code, input.arguments());
        assertDenied(task, "INPUT_SCHEMA_REJECTED");
    }

    private Actor actor(ToolCode code) { return actor(code,null); }
    private Actor actor(ToolCode code, String sourcePage) {
        long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String role = "READCAT_" + suffix, label = "readcat-" + suffix;
        jdbc.update("INSERT INTO rbac_role(tenant_id,code,name,description,builtin) VALUES (?,?,?,'isolated read catalog fixture',false)", tenant, role, label);
        long user = jdbc.queryForObject("INSERT INTO app_user(tenant_id,username,display_name,password,email,role) VALUES (?,?,?,'synthetic fixture',?,?) RETURNING id", Long.class,
                tenant, label, label, label + "@example.invalid", role);
        var routes = pages.businessPageIdsForTool(code.code()).stream().sorted().toList();
        assertThat(routes).as("business route: " + code.code()).isNotEmpty();
        var permissions = new LinkedHashSet<>(catalog.find(code.code()).orElseThrow().requiredPermissions());
        routes.forEach(page -> permissions.add("route:" + page));
        String page = sourcePage == null ? routes.get(0) : sourcePage;
        permissions.addAll(pages.find(page).orElseThrow().requiredPermissions());
        pages.prerequisitePageIdsForTool(code.code()).forEach(prerequisite -> permissions.add("route:" + prerequisite));
        permissions.add("agent:tool:" + code.code());
        for (String permission : permissions) jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,?)", tenant, role, permission);
        jdbc.update("INSERT INTO data_scope(tenant_id,role_code,scope_type) VALUES (?,?,'SELF')", tenant, role);
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=false WHERE tenant_id=?", tenant);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code=?", code.code());
        jdbc.update("DELETE FROM agent_page_action_policy WHERE tenant_id=? AND page_id=? AND tool_code=?", tenant, page, code.code());
        return new Actor(tenant, user, role, label, page);
    }

    private FrozenReadTaskFixture.Task frozen(Actor actor, ToolCode code, com.fasterxml.jackson.databind.JsonNode arguments) {
        return new FrozenReadTaskFixture(jdbc, json, hashing, catalog).create(actor.tenant(), actor.user(), actor.page(), code.code(), arguments, FrozenReadTaskFixture.State.QUEUED);
    }

    private String run(FrozenReadTaskFixture.Task task) {
        worker.poll();
        await().atMost(Duration.ofSeconds(25)).until(() -> {
            worker.poll(); // Exercise bounded production retries too; a prior queued attempt cannot starve later cases.
            return !List.of("QUEUED", "RUNNING").contains(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, task.id()));
        });
        return jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, task.id());
    }

    private void assertDenied(FrozenReadTaskFixture.Task task, String reason) {
        assertThat(run(task)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT result IS NULL FROM agent_task_step WHERE task_id=?", Boolean.class, task.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked", Long.class, task.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND decision_code=? AND outcome='REJECTED'", Long.class, task.id(), reason)).isEqualTo(1);
    }

    private record Actor(long tenant, long user, String role, String label, String page) { }
}
