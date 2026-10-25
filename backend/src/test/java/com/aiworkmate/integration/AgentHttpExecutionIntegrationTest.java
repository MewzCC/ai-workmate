package com.aiworkmate.integration;

import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.task.AgentTaskEventService;
import com.aiworkmate.integration.FrozenReadTaskFixture.Task;
import com.aiworkmate.integration.AgentHttpTestFixture.Actor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Real loopback HTTP, login Cookie, execution and SSE; frozen plans intentionally skip the LLM. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "AGENT_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AgentHttpExecutionIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final String JWT_SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    private static final HttpClient HTTP = AgentHttpTestFixture.client();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private AgentHashing hashing;
    @Autowired private ToolCatalog catalog;
    @Autowired private PasswordEncoder passwords;
    @Autowired private AgentTaskEventService events;
    private long tenant;
    private Actor owner;
    private AgentHttpTestFixture http;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        ENVIRONMENT.register(registry);
        registry.add("server.address", () -> "127.0.0.1");
        registry.add("jwt.secret", () -> JWT_SECRET);
        registry.add("app.auth.cookie-name", () -> "oa_http_it");
        registry.add("app.auth.cookie-secure", () -> "false");
        registry.add("spring.ai.openai.api-key", () -> "development-only-unconfigured");
        registry.add("agent.enabled", () -> "true");
        registry.add("agent.execution-enabled", () -> "true");
        registry.add("agent.worker.poll-delay-ms", () -> "3600000");
        registry.add("agent.worker.heartbeat-delay-ms", () -> "3600000");
    }

    @BeforeEach void prepareActor() throws Exception {
        http = new AgentHttpTestFixture(jdbc, json, passwords, port, "en-US");
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=false WHERE tenant_id=?", tenant);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code='notification.mine'");
        jdbc.update("DELETE FROM agent_page_action_policy WHERE tenant_id=? AND page_id='messages'", tenant);
        owner = loginNewActor(tenant);
    }

    @AfterEach void stopUnusedFixtures() {
        if (owner == null) return;
        jdbc.update("UPDATE agent_task SET status='CANCELLED' WHERE user_id=? AND status IN ('PLAN_READY','QUEUED')", owner.id());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM agent_task WHERE user_id=? AND status='RUNNING'", Long.class, owner.id())).isZero());
    }

    @Test void loginExecuteDetailAndLiveSseDeliverActualOwnedResults() throws Exception {
        Task task = plan();
        String title = "owned HTTP notice";
        jdbc.update("INSERT INTO notification(tenant_id,user_id,type,title,content) VALUES (?,?,'SYSTEM',?,'controlled HTTP result')", tenant, owner.id(), title);
        var stream = HTTP.send(request("GET", taskPath(task) + "/events", owner, null, null)
                .header("Accept", "text/event-stream").build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = stream.body()) {
            assertThat(stream.statusCode()).isEqualTo(200);
            assertThat(stream.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
            var accepted = execute(task, owner, UUID.randomUUID().toString());
            assertThat(accepted.statusCode()).isEqualTo(202);
            assertThat(json.readTree(accepted.body()).path("data").path("taskId").asText()).isEqualTo(task.number());
            awaitSuccess(task);
            String live = drain(body);
            assertThat(live).contains("event:step-started", "event:step-completed", "event:task-completed");
            List<Long> ids = eventIds(live);
            assertThat(ids).hasSizeGreaterThanOrEqualTo(4);
            for (int index = 1; index < ids.size(); index++) {
                assertThat(ids.get(index)).isGreaterThan(ids.get(index - 1));
            }
            long cursor = ids.get(ids.size() - 2);
            var replay = HTTP.send(request("GET", taskPath(task) + "/events", owner, null, null)
                    .header("Last-Event-ID", Long.toString(cursor)).build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream replayBody = replay.body()) {
                String resumed = drain(replayBody);
                assertThat(replay.statusCode()).isEqualTo(200);
                assertThat(eventIds(resumed)).isNotEmpty().allMatch(id -> id > cursor);
                assertThat(resumed).contains("event:task-completed").doesNotContain("event:step-started");
            }
        }
        var detail = get(taskPath(task), owner);
        JsonNode result = json.readTree(detail.body()).path("data").path("steps").get(0).path("result");
        assertThat(result.path("total").asLong()).isEqualTo(1);
        assertThat(result.path("items").get(0).path("title").asText()).isEqualTo(title);
        assertThat(detail.body()).doesNotContain("leaseToken", "confirmationToken");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked AND outcome='SUCCEEDED'", Long.class, task.id())).isEqualTo(1);
    }

    @Test void anonymousAndInvalidCookieRequestsNeverReadOrQueueTheTask() throws Exception {
        Task task = plan();
        assertThat(get(taskPath(task), null).statusCode()).isEqualTo(401);
        assertThat(execute(task, null, UUID.randomUUID().toString()).statusCode()).isEqualTo(401);
        assertThat(get(taskPath(task) + "/events", null).statusCode()).isEqualTo(401);
        var invalid = HTTP.send(request("GET", taskPath(task), null, null, null)
                .header("Cookie", "oa_http_it=invalid-token").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(invalid.statusCode()).isEqualTo(401);
        assertThat(json.readTree(invalid.body()).path("errorCode").asText()).isEqualTo("AUTH_TOKEN_INVALID");
        var invalidActor = new Actor(0, "invalid", "oa_http_it=invalid-token");
        assertThat(execute(task, invalidActor, UUID.randomUUID().toString()).statusCode()).isEqualTo(401);
        assertThat(get(taskPath(task) + "/events", invalidActor).statusCode()).isEqualTo(401);
        assertUntouched(task);
    }

    @Test void otherUserAndOtherTenantCannotReadExecuteOrSubscribe() throws Exception {
        Task task = plan();
        long otherTenant = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'HTTP tenant') RETURNING id", Long.class,
                "HTTP_" + UUID.randomUUID().toString().replace("-", ""));
        for (Actor stranger : List.of(loginNewActor(tenant), loginNewActor(otherTenant))) {
            assertThat(get(taskPath(task), stranger).statusCode()).isEqualTo(404);
            assertThat(execute(task, stranger, UUID.randomUUID().toString()).statusCode()).isEqualTo(404);
            var stream = HTTP.send(request("GET", taskPath(task) + "/events", stranger, null, null).build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = stream.body()) {
                String rejected = drain(body);
                assertThat(rejected).contains("event:task-failed", "RESOURCE_NOT_FOUND").doesNotContain(task.number(), "event:snapshot");
            }
        }
        assertUntouched(task);
    }

    @Test void theSameCookieObservesPermissionRevocationBeforeExecution() throws Exception {
        Task task = plan();
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='notification:read:self'", tenant, owner.role());
        var me = get("/api/auth/me", owner);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).doesNotContain("notification:read:self");
        var denied = execute(task, owner, UUID.randomUUID().toString());
        assertThat(denied.statusCode()).isEqualTo(403);
        assertUntouched(task);
    }

    @Test void disabledPageActionIsRejectedBeforeQueueing() throws Exception {
        Task task = plan();
        jdbc.update("INSERT INTO agent_page_action_policy(tenant_id,page_id,tool_code,enabled,updated_by) VALUES (?,'messages','notification.mine',false,?)", tenant, owner.id());
        assertThat(execute(task, owner, UUID.randomUUID().toString()).statusCode()).isEqualTo(403);
        assertUntouched(task);
    }

    @Test void concurrentDuplicateRequestsExecuteOnlyOnceAndKeysCannotMoveToAnotherTask() throws Exception {
        Task task = plan();
        String key = UUID.randomUUID().toString();
        var first = HTTP.sendAsync(executionRequest(task, owner, key), HttpResponse.BodyHandlers.ofString());
        var second = HTTP.sendAsync(executionRequest(task, owner, key), HttpResponse.BodyHandlers.ofString());
        assertThat(first.get(15, TimeUnit.SECONDS).statusCode()).isEqualTo(202);
        assertThat(second.get(15, TimeUnit.SECONDS).statusCode()).isEqualTo(202);
        awaitSuccess(task);
        assertThat(jdbc.queryForObject("SELECT tool_call_count FROM agent_task WHERE id=?", Integer.class, task.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked", Long.class, task.id())).isEqualTo(1);
        Task another = plan();
        var conflict = execute(another, owner, key);
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(json.readTree(conflict.body()).path("errorCode").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertUntouched(another);
    }

    @Test void changedPlanHashIsRejectedWithoutQueueOrAuditInvocation() throws Exception {
        Task task = plan();
        String payload = json.createObjectNode().put("planVersion", 1).put("planHash", "sha256:" + "0".repeat(64)).toString();
        var rejected = HTTP.send(request("POST", taskPath(task) + "/execute", owner, UUID.randomUUID().toString(), payload).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(json.readTree(rejected.body()).path("errorCode").asText()).isEqualTo("GATEWAY_STALE");
        assertUntouched(task);
    }

    private Task plan() {
        Task task = new FrozenReadTaskFixture(jdbc, json, hashing, catalog)
                .create(tenant, owner.id(), "messages", "notification.mine", json.createObjectNode(), FrozenReadTaskFixture.State.PLAN_READY);
        events.publish(task.id(), "snapshot", json.createObjectNode().put("taskId", task.number()).put("status", "PLAN_READY"), "http-it");
        return task;
    }

    private Actor loginNewActor(long tenantId) throws Exception {
        return http.createAndLogin(tenantId, List.of("route:messages", "notification:read:self", "agent:tool:notification.mine"));
    }

    private HttpRequest.Builder request(String method, String path, Actor actor, String key, String body) {
        return http.request(method, path, actor, key, body);
    }

    private HttpRequest executionRequest(Task task, Actor actor, String key) {
        return request("POST", taskPath(task) + "/execute", actor, key,
                json.createObjectNode().put("planVersion", 1).put("planHash", task.planHash()).toString()).build();
    }

    private HttpResponse<String> execute(Task task, Actor actor, String key) throws Exception {
        return HTTP.send(executionRequest(task, actor, key), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, Actor actor) throws Exception {
        return HTTP.send(request("GET", path, actor, null, null).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void awaitSuccess(Task task) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var response = get(taskPath(task), owner);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json.readTree(response.body()).path("data").path("status").asText()).isEqualTo("SUCCEEDED");
        });
    }

    private void assertUntouched(Task task) {
        assertThat(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, task.id())).isEqualTo("PLAN_READY");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=?", Long.class, task.id())).isZero();
    }

    private String taskPath(Task task) { return "/api/ai/tasks/" + task.number(); }

    private String drain(InputStream stream) throws Exception {
        var executor = Executors.newSingleThreadExecutor(runnable -> {
            var thread = new Thread(runnable, "agent-http-sse-test");
            thread.setDaemon(true);
            return thread;
        });
        var read = executor.submit(() -> stream.readNBytes(65537));
        try {
            byte[] body = read.get(10, TimeUnit.SECONDS);
            assertThat(body.length).isLessThanOrEqualTo(65536);
            return new String(body, StandardCharsets.UTF_8);
        } finally {
            stream.close();
            read.cancel(true);
            executor.shutdownNow();
        }
    }

    private List<Long> eventIds(String stream) {
        return Pattern.compile("(?m)^id:(\\d+)\\r?$").matcher(stream).results().map(match -> Long.parseLong(match.group(1))).toList();
    }

}
