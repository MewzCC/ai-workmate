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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentLiveClarificationBrowserIT extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENV = new AgentIntegrationEnvironment();
    private static final int UI_PORT = AgentBrowserFixture.reserveUiPort();
    @Override protected AgentIntegrationEnvironment environment() { return ENV; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        LiveModelTestConfiguration.register(p, ENV, "oa_browser_it");
        p.add("app.cors.allowed-origins", () -> "http://127.0.0.1:" + UI_PORT);
        p.add("agent.write-tools-enabled", () -> "true");
    }
    @ParameterizedTest @ValueSource(strings = {"drawer", "workspace"})
    void questionSurvivesNavigationOrReloadAndTheReplyStillRequiresConfirmation(String surface) throws Exception {
        LiveLeaveBrowserFixture.run(jdbc, json, passwords, ENV, port, UI_PORT, surface, true);
    }
}
