package com.aiworkmate.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.task.AgentHashing;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit real-model browser acceptance; excluded from offline Surefire naming patterns. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentLiveBrowserIT extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private PasswordEncoder passwords;
    @Autowired private AgentHashing hashing;
    @Autowired private ToolCatalog catalog;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        LiveModelTestConfiguration.register(properties, ENVIRONMENT, "oa_browser_it");
    }

    @Test void browserReadsActualResultsNavigatesAndPreservesTheDrawerAcrossRoutes() throws Exception {
        long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=false WHERE tenant_id=?", tenant);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code IN ('notification.mine','todo.query')");
        jdbc.update("DELETE FROM agent_page_action_policy WHERE tenant_id=? AND page_id IN ('dashboard','todo','messages','ai-workspace')", tenant);
        var http = new AgentHttpTestFixture(jdbc, json, passwords, port, "zh-CN");
        var owner = http.createAndLogin(tenant, List.of("route:dashboard", "route:ai-workspace", "route:messages", "route:todo", "route:ai-tasks",
                "notification:read:self", "todo:read", "agent:tool:notification.mine", "agent:tool:todo.query"));
        var applicant = http.createAndLogin(tenant, List.of());
        LeaveReadFixture.pending(jdbc, tenant, applicant.id(), owner.id());
        LeaveReadFixture.pending(jdbc, tenant, applicant.id(), applicant.id());
        String applicantName = jdbc.queryForObject("SELECT username FROM app_user WHERE id=?", String.class, applicant.id());
        String notice = "browser-notice-" + UUID.randomUUID();
        jdbc.update("INSERT INTO notification(tenant_id,user_id,type,title,content) VALUES (?,?,'SYSTEM',?,'synthetic browser data')", tenant, owner.id(), notice);
        var cancellable = new FrozenReadTaskFixture(jdbc, json, hashing, catalog)
                .create(tenant, owner.id(), "messages", "notification.mine", json.createObjectNode(), FrozenReadTaskFixture.State.PLAN_READY);
        Path repository = Path.of("..").toAbsolutePath().normalize();
        Path log = Path.of("target", "agent-it", ENVIRONMENT.schema(), "browser.log").toAbsolutePath();
        Files.createDirectories(log.getParent());
        var launcher = new ProcessBuilder("node", repository.resolve("scripts/verify-agent-browser.mjs").toString())
                .directory(repository.resolve("fonted-oa").toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        // The browser/Vite process never inherits server model or database credentials.
        var original = System.getenv();
        launcher.environment().clear();
        for (String name : List.of("Path", "PATH", "SystemRoot", "WINDIR", "COMSPEC", "PATHEXT", "TEMP", "TMP",
                "USERPROFILE", "LOCALAPPDATA", "APPDATA", "HOME", "PLAYWRIGHT_BROWSERS_PATH")) {
            if (original.containsKey(name)) launcher.environment().put(name, original.get(name));
        }
        launcher.environment().put("AGENT_BROWSER_API_PORT", Integer.toString(port));
        launcher.environment().put("AGENT_BROWSER_COOKIE", owner.cookie());
        launcher.environment().put("AGENT_BROWSER_APPLICANT", applicantName);
        launcher.environment().put("AGENT_BROWSER_NOTICE", notice);
        launcher.environment().put("AGENT_BROWSER_CANCELLABLE_TASK", cancellable.number());
        Process browser = launcher.start();
        try {
            assertThat(browser.waitFor(210, TimeUnit.SECONDS)).as("owned browser completion budget").isTrue();
            Files.readAllLines(log).stream().filter(line -> line.startsWith("Browser acceptance:")).forEach(System.out::println);
            assertThat(browser.exitValue()).as("browser acceptance exit code; inspect only the private local log").isZero();
        } finally {
            if (browser.isAlive()) {
                var owned = new ArrayList<>(browser.descendants().toList());
                owned.add(browser.toHandle());
                owned.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroy);
                try {
                    CompletableFuture.allOf(owned.stream().map(ProcessHandle::onExit).toArray(CompletableFuture[]::new)).get(3, TimeUnit.SECONDS);
                } catch (TimeoutException timeout) {
                    // Escalate only the handles captured from our still-owned browser process.
                } finally {
                    owned.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
                }
                CompletableFuture.allOf(owned.stream().map(ProcessHandle::onExit).toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
                assertThat(owned).noneMatch(ProcessHandle::isAlive);
            }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE user_id=? AND status='SUCCEEDED'", Long.class, owner.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE user_id=? AND handler_invoked AND outcome='SUCCEEDED'", Long.class, owner.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, cancellable.id())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=?", Long.class, cancellable.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT write_tools_enabled FROM agent_tenant_policy WHERE tenant_id=?", Boolean.class, tenant)).isFalse();
    }
}
