package com.aiworkmate.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/** Real administrator role changes, live user refresh and delayed reads in an owned database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentPermissionRefreshBrowserIT extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final int UI_PORT = AgentBrowserFixture.reserveUiPort();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private PasswordEncoder passwords;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        AgentBrowserFixture.registerLocal(properties, ENVIRONMENT, UI_PORT, SECRET);
    }

    @Test void revokesAndRestoresActualReadAuthorityWithoutReloginOrAcceptingOldResponses() throws Exception {
        long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        var http = new AgentHttpTestFixture(jdbc, json, passwords, port, "zh-CN");
        var permissions = List.of("route:messages", "route:dashboard", "notification:read:self");
        var reader = http.createAndLogin(tenant, permissions);
        var admin = http.createAndLogin(tenant, List.of("access:manage"));
        String notice = "permission-notice-" + UUID.randomUUID();
        jdbc.update("INSERT INTO notification(tenant_id,user_id,type,title,content) VALUES (?,?,'SYSTEM',?,'Synthetic private content')", tenant, reader.id(), notice);
        long version = jdbc.queryForObject("SELECT permission_version FROM app_user WHERE id=?", Long.class, reader.id());
        AgentBrowserFixture.run(ENVIRONMENT.schema(), port, UI_PORT, Map.of(
                "AGENT_BROWSER_MODE", "permissions", "AGENT_BROWSER_COOKIE", reader.cookie(),
                "AGENT_BROWSER_ADMIN_COOKIE", admin.cookie(), "AGENT_BROWSER_READER_ROLE", reader.role(),
                "AGENT_BROWSER_NOTICE", notice));
        assertThat(jdbc.queryForList("SELECT permission_code FROM rbac_role_permission WHERE tenant_id=? AND role_code=?", String.class, tenant, reader.role()))
                .containsExactlyInAnyOrderElementsOf(permissions);
        assertThat(jdbc.queryForObject("SELECT permission_version FROM app_user WHERE id=?", Long.class, reader.id())).isEqualTo(version + 2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM access_audit_log WHERE operator_user_id=? AND action='UPDATE_ROLE_PERMISSIONS' AND target_id=? AND result='SUCCESS'", Long.class, admin.id(), reader.role())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE user_id=? AND read_flag=false", Long.class, reader.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE user_id IN (?,?)", Long.class, reader.id(), admin.id())).isZero();
    }
}
