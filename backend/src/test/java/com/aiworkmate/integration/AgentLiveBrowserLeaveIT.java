package com.aiworkmate.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Real browser/model confirmation, confined to disposable data and a synthetic user. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentLiveBrowserLeaveIT extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final int UI_PORT = AgentBrowserFixture.reserveUiPort();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private PasswordEncoder passwords;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        LiveModelTestConfiguration.register(p, ENVIRONMENT, "oa_browser_it");
        p.add("app.cors.allowed-origins", () -> "http://127.0.0.1:" + UI_PORT);
        p.add("agent.write-tools-enabled", () -> "true");
    }

    @ParameterizedTest
    @ValueSource(strings = {"drawer", "workspace"})
    void requiresConfirmationAndKeepsTheActualSubmittedResultAcrossRoutes(String surface) throws Exception {
        LiveLeaveBrowserFixture.run(jdbc, json, passwords, ENVIRONMENT, port, UI_PORT, surface, false);
    }
}
