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

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Human UI writes in an owned database. Does not grant or exercise Agent write authority. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AssetLifecycleBrowserIT extends AbstractIsolatedAgentIntegrationTest {
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

    @Test void completesTheLifecycleWithRealSelectorsVersionsAndAuditsWithoutExtraHrPermission() throws Exception {
        long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        var http = new AgentHttpTestFixture(jdbc, json, passwords, port, "zh-CN");
        var writer = http.createAndLogin(tenant, List.of("route:asset-ledger", "assets:read", "asset:write"));
        long departmentA = department(tenant, "Lifecycle department A"), departmentB = department(tenant, "Lifecycle department B");
        long ownerA = person(tenant, departmentA, "Lifecycle owner A"), ownerB = person(tenant, departmentB, "Lifecycle owner B");
        String code = "lifecycle-" + UUID.randomUUID();
        long asset = jdbc.queryForObject("INSERT INTO asset_ledger(tenant_id,asset_code,name,category,status,department_id,version) VALUES (?,?,'Lifecycle asset','OFFICE','IDLE',?,0) RETURNING id",
                Long.class, tenant, code, departmentA);
        long invocationCount = jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation", Long.class);
        AgentBrowserFixture.run(ENVIRONMENT.schema(), port, UI_PORT, Map.of(
                "AGENT_BROWSER_MODE", "asset-lifecycle", "AGENT_BROWSER_COOKIE", writer.cookie(),
                "AGENT_BROWSER_ASSET_ID", Long.toString(asset), "AGENT_BROWSER_ASSET_CODE", code,
                "AGENT_BROWSER_DEPARTMENT_A", Long.toString(departmentA), "AGENT_BROWSER_DEPARTMENT_B", Long.toString(departmentB),
                "AGENT_BROWSER_OWNER_A", Long.toString(ownerA), "AGENT_BROWSER_OWNER_B", Long.toString(ownerB)));
        var finalRow = jdbc.queryForMap("SELECT status,version,department_id,owner_user_id,remark FROM asset_ledger WHERE id=?", asset);
        assertThat(finalRow.get("status")).isEqualTo("SCRAPPED");
        assertThat(((Number) finalRow.get("version")).intValue()).isEqualTo(8);
        assertThat(((Number) finalRow.get("department_id")).longValue()).isEqualTo(departmentB);
        assertThat(finalRow.get("owner_user_id")).isNull();
        assertThat(finalRow.get("remark")).isEqualTo("Lifecycle edited metadata");
        assertThat(jdbc.queryForList("SELECT operation_type FROM asset_operation WHERE asset_id=? ORDER BY id", String.class, asset))
                .containsExactly("CLAIM", "TRANSFER", "RETURN", "REPAIR_START", "REPAIR_COMPLETE", "INVENTORY", "SCRAP");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asset_operation WHERE asset_id=? AND operator_user_id=? AND tenant_id=? AND agent_operation_key IS NULL", Long.class, asset, writer.id(), tenant)).isEqualTo(7);
        assertThat(jdbc.queryForList("SELECT action FROM business_audit_log WHERE resource_type='ASSET_LEDGER' AND resource_id=? AND actor_user_id=? AND tenant_id=? AND result='SUCCESS' ORDER BY id", String.class, Long.toString(asset), writer.id(), tenant))
                .containsExactly("UPDATE", "CLAIM", "TRANSFER", "RETURN", "REPAIR_START", "REPAIR_COMPLETE", "INVENTORY", "SCRAP");

        // Refuse invalid lifecycle actions even if a client bypasses disabled buttons.
        String root = "/api/admin-assets/assets/" + asset;
        for (String suffix : List.of("claim", "transfer", "repairs")) {
            var response = AgentHttpTestFixture.client().send(http.request("POST", root + "/" + suffix, writer, null,
                    json.createObjectNode().put("version", 8).put("targetDepartmentId", departmentA).put("targetOwnerUserId", ownerA).put("reason", "Synthetic invalid transition").toString()).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(409);
        }
        var reader = http.createAndLogin(tenant, List.of("assets:read"));
        assertThat(AgentHttpTestFixture.client().send(http.request("POST", root + "/scrap", reader, null,
                "{\"version\":8,\"reason\":\"Synthetic unauthorized request\"}").build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        long foreignTenant = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Lifecycle foreign tenant') RETURNING id", Long.class,
                "LC_" + UUID.randomUUID().toString().replace("-", ""));
        var foreign = http.createAndLogin(foreignTenant, List.of("assets:read", "asset:write"));
        assertThat(AgentHttpTestFixture.client().send(http.request("GET", root, foreign, null, null).build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
        assertThat(AgentHttpTestFixture.client().send(http.request("POST", root + "/scrap", foreign, null,
                "{\"version\":8,\"reason\":\"Synthetic foreign request\"}").build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asset_operation WHERE asset_id=?", Long.class, asset)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT version FROM asset_ledger WHERE id=?", Integer.class, asset)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asset_ledger WHERE tenant_id=? AND name='Unsaved replacement draft'", Long.class, tenant)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE user_id=?", Long.class, writer.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation", Long.class)).isEqualTo(invocationCount);
    }

    private long department(long tenant, String name) {
        return jdbc.queryForObject("INSERT INTO department(tenant_id,code,name,status) VALUES (?,?,?,1) RETURNING id", Long.class, tenant, UUID.randomUUID().toString(), name);
    }
    private long person(long tenant, long department, String name) {
        String suffix = UUID.randomUUID().toString();
        return jdbc.queryForObject("INSERT INTO app_user(tenant_id,username,password,email,display_name,department_id,status) VALUES (?,?,'unusable synthetic password',?,?,?,1) RETURNING id",
                Long.class, tenant, suffix, suffix + "@example.invalid", name, department);
    }
}
