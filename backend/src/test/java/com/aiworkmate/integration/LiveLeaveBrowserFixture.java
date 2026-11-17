package com.aiworkmate.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Same real leave, actor and browser assertions for direct and clarified requests. */
final class LiveLeaveBrowserFixture {
    private LiveLeaveBrowserFixture() { }
    static void run(JdbcTemplate jdbc, ObjectMapper json, PasswordEncoder passwords,
                    AgentIntegrationEnvironment environment, int port, int uiPort, String surface, boolean clarification) throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(environment.schema());
        long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=true WHERE tenant_id=?", tenant);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code='leave.apply'");
        jdbc.update("DELETE FROM agent_page_action_policy WHERE tenant_id=? AND page_id IN ('leave-application','ai-workspace') AND tool_code='leave.apply'", tenant);
        var http = new AgentHttpTestFixture(jdbc, json, passwords, port, "zh-CN");
        var actor = http.createAndLogin(tenant, List.of("route:ai-workspace", "route:leave-application", "route:my-applications",
                "leave:create", "leave:read:self", "agent:tool:leave.apply"));
        long approver = LeaveWriteFixture.linkedApprover(jdbc, tenant, actor.id());
        String reason = "browser-leave-" + UUID.randomUUID();
        String date = LocalDate.now().plusDays(3).toString();
        AgentBrowserFixture.run(environment.schema(), port, uiPort, Map.of(
                "AGENT_BROWSER_COOKIE", actor.cookie(), "AGENT_BROWSER_MODE", "leave-write", "AGENT_BROWSER_SURFACE", surface,
                "AGENT_BROWSER_CLARIFICATION", Boolean.toString(clarification),
                "AGENT_BROWSER_LEAVE_REASON", reason, "AGENT_BROWSER_LEAVE_DATE", date));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM leave_application WHERE tenant_id=? AND applicant_user_id=? AND approver_user_id=?
                  AND reason=? AND leave_type='PERSONAL' AND status='PENDING' AND duration_half_days=2
                  AND start_date=?::date AND end_date=?::date AND start_period='AM' AND end_period='PM'
                """, Long.class, tenant, actor.id(), approver, reason, date, date)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_application WHERE applicant_user_id=?", Long.class, actor.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE user_id=? AND status='SUCCEEDED'", Long.class, actor.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE user_id=?", Long.class, actor.id())).isEqualTo(clarification ? 2 : 1);
        if (clarification)
            assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE user_id=? AND status='CANCELLED'", Long.class, actor.id())).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE user_id=? AND handler_invoked AND outcome='SUCCEEDED'", Long.class, actor.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE tenant_id=? AND actor_user_id=? AND action='AGENT_APPLY' AND result='SUCCESS'", Long.class, tenant, actor.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE tenant_id=? AND assignee_user_id=? AND business_type='LEAVE_APPLICATION' AND status='PENDING'", Long.class, tenant, approver)).isEqualTo(1);
        if ("workspace".equals(surface)) {
            long conversation = jdbc.queryForObject("SELECT id FROM conversation WHERE user_id=?", Long.class, actor.id());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM message WHERE conversation_id=?", Long.class, conversation)).isEqualTo(clarification ? 4 : 2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM message WHERE conversation_id=? AND role='assistant' AND status='success' AND source_tool_code='leave.apply' AND content LIKE '%等待审批%'", Long.class, conversation)).isEqualTo(1);
        }
    }
}
