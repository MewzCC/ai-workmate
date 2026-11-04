package com.aiworkmate.integration;

import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.task.AgentHashing;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Frozen fault-state fixtures; browser uses real reads/cancellation, never a tool execution. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentRecoveryBrowserIT extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final int UI_PORT = AgentBrowserFixture.reserveUiPort();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private PasswordEncoder passwords;
    @Autowired private AgentHashing hashing;
    @Autowired private ToolCatalog catalog;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        AgentBrowserFixture.registerLocal(properties, ENVIRONMENT, UI_PORT, SECRET);
    }

    @Test void restoresFrozenTasksAndReconcilesCancellationWithoutRepeatingExecution() throws Exception {
        long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        var http = new AgentHttpTestFixture(jdbc, json, passwords, port, "zh-CN");
        var owner = http.createAndLogin(tenant, List.of("route:dashboard", "route:ai-workspace", "route:ai-tasks"));
        var write = new FrozenWriteTaskFixture(jdbc, json, hashing, catalog);
        var args = json.createObjectNode().put("leaveType", "PERSONAL")
                .put("startDate", LocalDate.now().plusDays(3).toString()).put("startPeriod", "AM")
                .put("endDate", LocalDate.now().plusDays(3).toString()).put("endPeriod", "PM").put("reason", "recovery fixture");
        args.put("approverUserId", LeaveWriteFixture.linkedApprover(jdbc, tenant, owner.id()));
        var waiting = write.create(tenant, owner.id(), "ai-workspace", "leave.apply", args);
        var queued = new FrozenReadTaskFixture(jdbc, json, hashing, catalog).create(tenant, owner.id(), "ai-workspace",
                "notification.mine", json.createObjectNode(), FrozenReadTaskFixture.State.QUEUED);
        var uncertain = write.create(tenant, owner.id(), "ai-workspace", "leave.apply", args);
        // Represent a previously recorded unknown outcome; no business write is performed by this fixture.
        jdbc.update("UPDATE agent_task SET status='PARTIALLY_SUCCEEDED',error_code='TOOL_RESULT_UNKNOWN',finished_at=CURRENT_TIMESTAMP WHERE id=?", uncertain.id());
        jdbc.update("UPDATE agent_task_step SET status='FAILED',error_code='TOOL_RESULT_UNKNOWN',finished_at=CURRENT_TIMESTAMP WHERE task_id=?", uncertain.id());
        String waitingTitle = "recovery-waiting-" + UUID.randomUUID();
        String queuedTitle = "recovery-queued-" + UUID.randomUUID();
        String unknownTitle = "recovery-unknown-" + UUID.randomUUID();
        long waitingChat = link(tenant, owner.id(), waiting.id(), waitingTitle);
        long queuedChat = link(tenant, owner.id(), queued.id(), queuedTitle);
        long unknownChat = link(tenant, owner.id(), uncertain.id(), unknownTitle);
        AgentBrowserFixture.run(ENVIRONMENT.schema(), port, UI_PORT, Map.of(
                "AGENT_BROWSER_MODE", "recovery", "AGENT_BROWSER_COOKIE", owner.cookie(),
                "AGENT_BROWSER_WAITING_TITLE", waitingTitle, "AGENT_BROWSER_WAITING_TASK", waiting.number(),
                "AGENT_BROWSER_QUEUED_TITLE", queuedTitle, "AGENT_BROWSER_QUEUED_TASK", queued.number(),
                "AGENT_BROWSER_UNKNOWN_TITLE", unknownTitle, "AGENT_BROWSER_UNKNOWN_TASK", uncertain.number()));
        assertThat(jdbc.queryForList("SELECT status FROM agent_task WHERE id IN (?,?)", String.class, waiting.id(), queued.id()))
                .containsOnly("CANCELLED").hasSize(2);
        assertThat(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, uncertain.id())).isEqualTo("PARTIALLY_SUCCEEDED");
        for (long conversation : List.of(waitingChat, queuedChat, unknownChat)) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM message WHERE conversation_id=? AND source_task_no IS NOT NULL", Long.class, conversation)).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE user_id=?", Long.class, owner.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_application WHERE applicant_user_id=?", Long.class, owner.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE user_id=? AND confirmation_token_hash IS NOT NULL", Long.class, owner.id())).isZero();
    }

    private long link(long tenant, long user, long task, String title) {
        long conversation = jdbc.queryForObject("INSERT INTO conversation(tenant_id,user_id,title,model) VALUES (?,?,?,'deepseek-chat') RETURNING id",
                Long.class, tenant, user, title);
        jdbc.update("UPDATE agent_task SET conversation_id=? WHERE id=?", conversation, task);
        jdbc.update("INSERT INTO message(conversation_id,role,content,status) VALUES (?,'user','Synthetic recovery request','success')", conversation);
        return conversation;
    }
}
