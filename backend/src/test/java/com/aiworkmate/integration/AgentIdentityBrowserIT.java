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

/** Account changes and delayed real HTTP responses inside a disposable environment. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentIdentityBrowserIT extends AbstractIsolatedAgentIntegrationTest {
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

    @Test void switchesAccountsWithoutRetainingMessagesDraftsOrDelayedResponses() throws Exception {
        long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        var http = new AgentHttpTestFixture(jdbc, json, passwords, port, "zh-CN");
        var permissions = List.of("route:ai-workspace", "route:dashboard");
        var first = http.createAndLogin(tenant, permissions);
        var second = http.createAndLogin(tenant, permissions);
        String suffix = UUID.randomUUID().toString();
        String wallpaper = "identity-fixture-" + suffix + ".png";
        jdbc.update("UPDATE app_user SET wallpaper=? WHERE id=?", wallpaper, first.id());
        String firstTitle = "identity-A-" + suffix, delayedTitle = "identity-delayed-" + suffix, secondTitle = "identity-B-" + suffix;
        String firstMessage = "private-A-" + suffix, delayedMessage = "private-delayed-" + suffix, secondMessage = "private-B-" + suffix;
        seed(tenant, first.id(), firstTitle, firstMessage);
        long delayed = seed(tenant, first.id(), delayedTitle, delayedMessage);
        seed(tenant, second.id(), secondTitle, secondMessage);
        AgentBrowserFixture.run(ENVIRONMENT.schema(), port, UI_PORT, Map.ofEntries(
                Map.entry("AGENT_BROWSER_MODE", "identity"), Map.entry("AGENT_BROWSER_COOKIE", first.cookie()),
                Map.entry("AGENT_BROWSER_SECOND_COOKIE", second.cookie()), Map.entry("AGENT_BROWSER_SECOND_USER", Long.toString(second.id())),
                Map.entry("AGENT_BROWSER_FIRST_TITLE", firstTitle), Map.entry("AGENT_BROWSER_DELAYED_TITLE", delayedTitle),
                Map.entry("AGENT_BROWSER_SECOND_TITLE", secondTitle), Map.entry("AGENT_BROWSER_FIRST_MESSAGE", firstMessage),
                Map.entry("AGENT_BROWSER_DELAYED_MESSAGE", delayedMessage), Map.entry("AGENT_BROWSER_SECOND_MESSAGE", secondMessage),
                Map.entry("AGENT_BROWSER_DELAYED_CONVERSATION", Long.toString(delayed))));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message WHERE conversation_id IN (SELECT id FROM conversation WHERE user_id IN (?,?))", Long.class, first.id(), second.id())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE user_id IN (?,?)", Long.class, first.id(), second.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT wallpaper FROM app_user WHERE id=?", String.class, first.id())).isEqualTo(wallpaper);
        assertThat(jdbc.queryForObject("SELECT wallpaper FROM app_user WHERE id=?", String.class, second.id())).isNull();
    }

    private long seed(long tenant, long user, String title, String text) {
        long id = jdbc.queryForObject("INSERT INTO conversation(tenant_id,user_id,title,model) VALUES (?,?,?,'deepseek-chat') RETURNING id", Long.class, tenant, user, title);
        jdbc.update("INSERT INTO message(conversation_id,role,content,status) VALUES (?,'assistant',?,'success')", id, text);
        return id;
    }
}
