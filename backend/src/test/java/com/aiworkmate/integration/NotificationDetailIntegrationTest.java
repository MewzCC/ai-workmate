package com.aiworkmate.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationDetailIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor owner;
    private long tenant, notification;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        AgentBrowserFixture.registerLocal(p, ENVIRONMENT, AgentBrowserFixture.reserveUiPort(), SECRET);
    }
    @BeforeEach void setup() throws Exception {
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        http = new AgentHttpTestFixture(jdbc, json, passwords, port, "en-US");
        owner = http.createAndLogin(tenant, List.of("notification:read:self"));
        notification = jdbc.queryForObject("INSERT INTO notification(tenant_id,user_id,type,title,content) VALUES (?,?,'SYSTEM','Synthetic notice','Owned synthetic content') RETURNING id",
                Long.class, tenant, owner.id());
    }
    private HttpResponse<String> read(AgentHttpTestFixture.Actor actor, long id) throws Exception {
        return AgentHttpTestFixture.client().send(http.request("GET", "/api/notifications/" + id, actor, null, null).build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void returnsOwnedDetailWithoutChangingReadStateOrAudit() throws Exception {
        long audits = jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE actor_user_id=?", Long.class, owner.id());
        var response = read(owner, notification);
        assertThat(response.statusCode()).isEqualTo(200);
        var data = json.readTree(response.body()).path("data");
        assertThat(data.path("id").asLong()).isEqualTo(notification);
        assertThat(data.path("content").asText()).isEqualTo("Owned synthetic content");
        assertThat(data.path("read").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT read_flag FROM notification WHERE id=?", Boolean.class, notification)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE actor_user_id=?", Long.class, owner.id())).isEqualTo(audits);
    }
    @Test void refusesAnotherUserAndAnotherTenantWithoutLeakingContent() throws Exception {
        long otherTenant = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Synthetic foreign tenant') RETURNING id", Long.class, "NOTICE_" + UUID.randomUUID().toString().replace("-", ""));
        for (var actor : List.of(http.createAndLogin(tenant, List.of("notification:read:self")), http.createAndLogin(otherTenant, List.of("notification:read:self")))) {
            var response = read(actor, notification);
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.body()).doesNotContain("Owned synthetic content", "Synthetic notice");
        }
    }
    @Test void rechecksRevokedPermissionWithTheSameCookie() throws Exception {
        assertThat(read(owner, notification).statusCode()).isEqualTo(200);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=?", tenant, owner.role());
        var response = read(owner, notification);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).doesNotContain("Owned synthetic content");
    }
    @Test void refusesUnknownAndInvalidIdentifiers() throws Exception {
        assertThat(read(null, notification).statusCode()).isEqualTo(401);
        assertThat(read(owner, Long.MAX_VALUE).statusCode()).isEqualTo(404);
        assertThat(read(owner, 0).statusCode()).isEqualTo(400);
        assertThat(read(owner, -1).statusCode()).isEqualTo(400);
    }
}
