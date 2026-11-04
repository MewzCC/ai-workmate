package com.aiworkmate.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

/** Real Cookie and live database RBAC; uses only fixture-owned PostgreSQL and Redis. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GenericApprovalWritePermissionIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    private AgentHttpTestFixture http;
    private long tenant;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        AgentBrowserFixture.registerLocal(properties, ENVIRONMENT, AgentBrowserFixture.reserveUiPort(), SECRET);
    }
    @BeforeEach void setup() {
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        http = new AgentHttpTestFixture(jdbc, json, passwords, port, "en-US");
    }

    @ParameterizedTest
    @CsvSource({"POST,/drafts,CREATE", "PUT,/999999/draft,UPDATE", "POST,/999999/submit,VERSION",
            "POST,/999999/cancel,VERSION", "POST,/999999/withdraw,VERSION", "POST,/999999/remind,VERSION",
            "POST,/999999/reopen,VERSION", "POST,'',CREATE"})
    void routeOnlyCannotMutateAnyGenericApplication(String method, String suffix, String payload) throws Exception {
        var actor = http.createAndLogin(tenant, List.of("route:approval-start"));
        long before = jdbc.queryForObject("SELECT count(*) FROM approval_application WHERE tenant_id=?", Long.class, tenant);
        String body = switch (payload) {
            case "CREATE" -> "{\"formKey\":\"expense\",\"formData\":{}}";
            case "UPDATE" -> "{\"formData\":{},\"version\":0}";
            default -> "{\"version\":0}";
        };
        var response = send(method, "/api/approval-applications" + suffix, actor, body);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(json.readTree(response.body()).path("errorCode").asText()).isEqualTo("PERMISSION_DENIED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_application WHERE tenant_id=?", Long.class, tenant)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_instance WHERE applicant_id=?", Long.class, actor.id())).isZero();
    }

    @Test void directSubmissionRequiresBothCreateAndSubmit() throws Exception {
        for (String permission : List.of("approval:create", "approval:submit")) {
            var actor = http.createAndLogin(tenant, List.of("route:approval-start", permission));
            assertThat(send("POST", "/api/approval-applications", actor,
                    "{\"formKey\":\"expense\",\"formData\":{}}").statusCode()).isEqualTo(403);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_application WHERE applicant_user_id=?", Long.class, actor.id())).isZero();
        }
    }

    @Test void sameCookieImmediatelyLosesAndRegainsDraftWriteAuthority() throws Exception {
        var actor = http.createAndLogin(tenant, List.of("route:approval-start", "approval:create", "approval:cancel"));
        String formKey = "permission-" + UUID.randomUUID().toString().substring(0, 16);
        jdbc.update("INSERT INTO approval_form(tenant_id,form_key,form_name,schema_json,status) VALUES (?,?,'Permission test','{\"fields\":[]}','ENABLED')", tenant, formKey);
        var created = send("POST", "/api/approval-applications/drafts", actor,
                json.createObjectNode().put("formKey", formKey).set("formData", json.createObjectNode()).toString());
        assertThat(created.statusCode()).isEqualTo(200);
        JsonNode draft = json.readTree(created.body()).path("data");
        long id = draft.path("id").asLong();
        assertThat(id).isPositive();
        assertThat(draft.path("canEditDraft").asBoolean()).isTrue();
        assertThat(draft.path("canCancel").asBoolean()).isTrue();
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code IN ('approval:create','approval:cancel')", tenant, actor.role());
        var detail = send("GET", "/api/approval-applications/" + id, actor, null);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(json.readTree(detail.body()).path("data").path("canEditDraft").asBoolean()).isFalse();
        assertThat(json.readTree(detail.body()).path("data").path("canCancel").asBoolean()).isFalse();
        assertThat(send("PUT", "/api/approval-applications/" + id + "/draft", actor,
                "{\"formData\":{},\"version\":0}").statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/approval-applications/" + id + "/cancel", actor, "{\"version\":0}").statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT version FROM approval_application WHERE id=?", Integer.class, id)).isZero();
        jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,'approval:cancel')", tenant, actor.role());
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='route:approval-start'", tenant, actor.role());
        assertThat(send("POST", "/api/approval-applications/" + id + "/cancel", actor, "{\"version\":0}").statusCode()).isEqualTo(403);
        jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,'route:approval-start')", tenant, actor.role());
        var cancelled = send("POST", "/api/approval-applications/" + id + "/cancel", actor, "{\"version\":0}");
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(json.readTree(cancelled.body()).path("data").path("status").asText()).isEqualTo("CANCELLED");
        assertThat(json.readTree(cancelled.body()).path("data").path("version").asInt()).isEqualTo(1);
    }

    private HttpResponse<String> send(String method, String path, AgentHttpTestFixture.Actor actor, String body) throws Exception {
        return AgentHttpTestFixture.client().send(http.request(method, path, actor, null, body).build(), HttpResponse.BodyHandlers.ofString());
    }
}
