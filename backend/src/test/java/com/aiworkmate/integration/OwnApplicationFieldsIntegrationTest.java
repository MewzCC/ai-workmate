package com.aiworkmate.integration;

import com.aiworkmate.dto.ApprovalDraftRequest;
import com.aiworkmate.service.GenericApprovalService;
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
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OwnApplicationFieldsIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired GenericApprovalService approvals;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor owner;
    private long tenant, id, form;
    private String schema;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) { AgentBrowserFixture.registerLocal(p, ENVIRONMENT, AgentBrowserFixture.reserveUiPort(), SECRET); }
    @BeforeEach void setup() throws Exception {
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        http = new AgentHttpTestFixture(jdbc, json, passwords, port, "en-US");
        owner = http.createAndLogin(tenant, List.of("approval:mine:read", "route:approval-start", "approval:create"));
        String key = "fields_" + UUID.randomUUID().toString().replace("-", "");
        var fields = json.createArrayNode();
        for (int index = 0; index < 60; index++) fields.addObject().put("name", "field" + index).put("label", "Field " + index).put("type", "text");
        schema = json.createObjectNode().set("fields", fields).toString();
        form = jdbc.queryForObject("INSERT INTO approval_form(tenant_id,form_key,form_name,schema_json,status,created_by) VALUES (?,?,?,?,'ENABLED',?) RETURNING id", Long.class, tenant, key, key, schema, owner.id());
        id = approvals.createDraft(owner.id(), new ApprovalDraftRequest(key, null, Map.of("field0", "first private field", "field59", "last private field"))).id();
    }
    private HttpResponse<String> read(AgentHttpTestFixture.Actor actor, long application, int page) throws Exception {
        return AgentHttpTestFixture.client().send(http.request("GET", "/api/approval-applications/mine/" + application + "/fields?fieldPage=" + page, actor, null, null).build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void pagesCurrentDraftAndFrozenHistoryWithoutExposingRawConfiguration() throws Exception {
        var first = read(owner, id, 1); assertThat(first.statusCode()).isEqualTo(200);
        var draft = json.readTree(first.body()).path("data");
        assertThat(draft.path("schemaSource").asText()).isEqualTo("CURRENT_DRAFT");
        assertThat(draft.path("fields")).hasSize(50); assertThat(draft.path("fieldsTotal").asInt()).isEqualTo(60);
        jdbc.update("UPDATE approval_application SET status='APPROVED',form_schema_snapshot=?,form_version_snapshot=7 WHERE id=?", schema, id);
        jdbc.update("UPDATE approval_form SET schema_json='{\"fields\":[]}',version=version+1 WHERE id=?", form);
        var second = read(owner, id, 2); assertThat(second.statusCode()).isEqualTo(200);
        var frozen = json.readTree(second.body()).path("data");
        assertThat(frozen.path("schemaSource").asText()).isEqualTo("SNAPSHOT");
        assertThat(frozen.path("formVersion").asInt()).isEqualTo(7); assertThat(frozen.path("fields")).hasSize(10);
        assertThat(frozen.toString()).contains("last private field").doesNotContain("schemaJson", "dataJson", "ruleSnapshot");
    }
    @Test void otherUsersAndTenantsCannotUseAuditorAuthorityToReadTheOwnedProjection() throws Exception {
        long foreign = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Synthetic tenant') RETURNING id", Long.class, "FIELD_" + UUID.randomUUID().toString().replace("-", ""));
        for (var actor : List.of(http.createAndLogin(tenant, List.of("approval:mine:read", "route:approval-start", "audit:read")),
                http.createAndLogin(foreign, List.of("approval:mine:read", "route:approval-start", "audit:read")))) {
            var response = read(actor, id, 1); assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.body()).doesNotContain("first private field");
        }
    }
    @Test void rechecksAuthenticationBothPermissionsAndRevocationWithTheSameCookie() throws Exception {
        assertThat(read(null, id, 1).statusCode()).isEqualTo(401);
        for (var permissions : List.of(List.of("approval:mine:read"), List.of("route:approval-start")))
            assertThat(read(http.createAndLogin(tenant, permissions), id, 1).statusCode()).isEqualTo(403);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='approval:mine:read'", tenant, owner.role());
        assertThat(read(owner, id, 1).statusCode()).isEqualTo(403);
    }
    @Test void refusesInvalidFieldPagesAndResourceIdentifiers() throws Exception {
        assertThat(read(owner, id, 0).statusCode()).isEqualTo(400); assertThat(read(owner, id, 3).statusCode()).isEqualTo(400);
        assertThat(read(owner, 0, 1).statusCode()).isEqualTo(400); assertThat(read(owner, Long.MAX_VALUE, 1).statusCode()).isEqualTo(404);
    }
}
