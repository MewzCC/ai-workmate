package com.aiworkmate.integration;

import com.aiworkmate.oa.page.OaPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/** Real routes and read APIs at four viewport sizes; never uses a developer account or database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OaAllPagesBrowserIT extends AbstractIsolatedAgentIntegrationTest {
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

    @Test void everyPageLoadsItsRealReadApisWithoutBlankScreensAtAllViewportSizes() throws Exception {
        long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        var permissions = jdbc.queryForList("SELECT code FROM rbac_permission ORDER BY code", String.class);
        var http = new AgentHttpTestFixture(jdbc, json, passwords, port, "zh-CN");
        var owner = http.createAndLogin(tenant, permissions);
        AgentBrowserFixture.run(ENVIRONMENT.schema(), port, UI_PORT, Map.of(
                "AGENT_BROWSER_COOKIE", owner.cookie(), "AGENT_BROWSER_MODE", "pages",
                "AGENT_BROWSER_PAGE_IDS", json.writeValueAsString(Arrays.stream(OaPage.values()).map(OaPage::routeKey).toList())));
    }
}
