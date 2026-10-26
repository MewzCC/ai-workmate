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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit real-model browser acceptance; excluded from offline Surefire naming patterns. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentLiveBrowserIT extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final int UI_PORT = AgentBrowserFixture.reserveUiPort();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private PasswordEncoder passwords;
    @Autowired private AgentHashing hashing;
    @Autowired private ToolCatalog catalog;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        LiveModelTestConfiguration.register(properties, ENVIRONMENT, "oa_browser_it");
        properties.add("app.cors.allowed-origins", () -> "http://127.0.0.1:" + UI_PORT);
    }

    @Test void browserReadsActualResultsNavigatesAndPreservesTheDrawerAcrossRoutes() throws Exception {
        long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=false WHERE tenant_id=?", tenant);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code IN ('notification.mine','todo.query','userPermission.mine.query')");
        jdbc.update("DELETE FROM agent_page_action_policy WHERE tenant_id=? AND page_id IN ('dashboard','todo','messages','ai-workspace')", tenant);
        var http = new AgentHttpTestFixture(jdbc, json, passwords, port, "zh-CN");
        var owner = http.createAndLogin(tenant, List.of("route:dashboard", "route:ai-workspace", "route:messages", "route:todo", "route:ai-tasks",
                "notification:read:self", "todo:read", "agent:tool:notification.mine", "agent:tool:todo.query",
                "user-permission:read:self", "agent:tool:userPermission.mine.query"));
        var applicant = http.createAndLogin(tenant, List.of());
        LeaveReadFixture.pending(jdbc, tenant, applicant.id(), owner.id());
        LeaveReadFixture.pending(jdbc, tenant, applicant.id(), applicant.id());
        String applicantName = jdbc.queryForObject("SELECT username FROM app_user WHERE id=?", String.class, applicant.id());
        String notice = "browser-notice-" + UUID.randomUUID();
        String chatMarker = "browser-chat-" + UUID.randomUUID();
        jdbc.update("INSERT INTO notification(tenant_id,user_id,type,title,content) VALUES (?,?,'SYSTEM',?,'synthetic browser data')", tenant, owner.id(), notice);
        var cancellable = new FrozenReadTaskFixture(jdbc, json, hashing, catalog)
                .create(tenant, owner.id(), "messages", "notification.mine", json.createObjectNode(), FrozenReadTaskFixture.State.PLAN_READY);
        AgentBrowserFixture.run(ENVIRONMENT.schema(), port, UI_PORT, Map.of(
                "AGENT_BROWSER_COOKIE", owner.cookie(), "AGENT_BROWSER_APPLICANT", applicantName,
                "AGENT_BROWSER_NOTICE", notice, "AGENT_BROWSER_CHAT_MARKER", chatMarker,
                "AGENT_BROWSER_CANCELLABLE_TASK", cancellable.number()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE user_id=? AND status='SUCCEEDED'", Long.class, owner.id())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE user_id=?", Long.class, owner.id())).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE user_id=? AND handler_invoked AND outcome='SUCCEEDED'", Long.class, owner.id())).isEqualTo(3);
        long conversation = jdbc.queryForObject("SELECT id FROM conversation WHERE user_id=?", Long.class, owner.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message WHERE conversation_id=?", Long.class, conversation)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message WHERE conversation_id=? AND role='assistant' AND status='success' AND source_task_no IS NULL AND content LIKE ?",
                Long.class, conversation, "%" + chatMarker + "%")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message WHERE conversation_id=? AND role='assistant' AND status='success' AND source_tool_code IN ('notification.mine','userPermission.mine.query')",
                Long.class, conversation)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message WHERE conversation_id=? AND status<>'success'", Long.class, conversation)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, cancellable.id())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=?", Long.class, cancellable.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT write_tools_enabled FROM agent_tenant_policy WHERE tenant_id=?", Boolean.class, tenant)).isFalse();
    }

}
