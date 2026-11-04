package com.aiworkmate.integration;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.service.ApprovalEngineService;
import com.fasterxml.jackson.databind.JsonNode;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApprovalMetadataIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired ApprovalEngineService service;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor actor;
    private long tenant;
    private String marker;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        AgentBrowserFixture.registerLocal(properties, ENVIRONMENT, AgentBrowserFixture.reserveUiPort(), SECRET);
    }
    @BeforeEach void setup() throws Exception {
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        http = new AgentHttpTestFixture(jdbc, json, passwords, port, "zh-CN");
        actor = http.createAndLogin(tenant, List.of("approval:create", "route:approval-start", "approval:read"));
        marker = "metadata_" + UUID.randomUUID().toString().replace("-", "");
    }
    @Test void applicantMetadataIsEnabledTenantScopedAndOmitsConfigurationIdentities() throws Exception {
        long form = form(tenant, marker, "ENABLED");
        process(tenant, form, nodes());
        form(tenant, marker + "_disabled", "DISABLED");
        long other = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Synthetic tenant') RETURNING id", Long.class, UUID.randomUUID().toString());
        form(other, marker, "ENABLED");
        var list = get(actor, "keyword=" + marker, 200);
        assertThat(list.path("total").asLong()).isEqualTo(1);
        var item = list.path("records").get(0);
        assertThat(item.path("fields")).isEmpty();
        assertThat(item.path("process").path("nodes")).isEmpty();
        var exact = get(actor, "formKey=" + marker, 200).path("records").get(0);
        assertThat(exact.path("fields").get(0).path("name").asText()).isEqualTo("reason");
        assertThat(exact.path("process").path("nodes")).hasSize(3);
        for (String field : List.of("createdBy", "targetKey", "nodeJson", "schemaJson", "password", "email"))
            assertThat(exact.findValues(field)).as(field).isEmpty();
    }
    @Test void endpointRequiresAuthenticationCreateAuthorityAndEntryRouteAndRechecksTheSameCookie() throws Exception {
        get(null, "", 401);
        get(http.createAndLogin(tenant, List.of("approval:read")), "", 403);
        get(http.createAndLogin(tenant, List.of("approval:create")), "", 403);
        get(actor, "keyword=" + marker, 200);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='approval:create'", tenant, actor.role());
        get(actor, "keyword=" + marker, 403);
    }
    @Test void templatePagesHaveStableTotalsAndRejectInvalidBounds() throws Exception {
        for (int i = 0; i < 45; i++) form(tenant, marker + "_" + i, "ENABLED");
        var ids = new java.util.LinkedHashSet<Long>();
        for (int page = 1; page <= 3; page++) {
            var result = get(actor, "keyword=" + marker + "&page=" + page + "&size=20", 200);
            assertThat(result.path("total").asLong()).isEqualTo(45);
            result.path("records").forEach(row -> assertThat(ids.add(row.path("formId").asLong())).isTrue());
        }
        assertThat(ids).hasSize(45);
        for (String query : List.of("page=0", "page=10001", "size=0", "size=101", "keyword=" + "x".repeat(121), "formKey=BAD"))
            get(actor, query, 400);
    }
    @Test void processDefinitionUsesExpectedTenantAndReturnsSemanticNodesOnly() {
        long process = process(tenant, form(tenant, marker, "ENABLED"), nodes());
        var result = service.readProcessDefinitionAgent(actor.id(), tenant, process);
        assertThat(result.processId()).isEqualTo(process);
        assertThat(result.nodes()).hasSize(3);
        assertThat(result.nodes().get(1).nodeType()).isEqualTo("APPROVAL");
        assertThatThrownBy(() -> service.readProcessDefinitionAgent(actor.id(), tenant + 1000, process))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getStatus().value()).isEqualTo(403));
        long other = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Synthetic tenant') RETURNING id", Long.class, UUID.randomUUID().toString());
        long foreign = process(other, form(other, marker, "ENABLED"), nodes());
        assertThatThrownBy(() -> service.readProcessDefinitionAgent(actor.id(), tenant, foreign))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getStatus().value()).isEqualTo(404));
    }
    @Test void rejectsAmbiguousOversizedAndUnpublishableProcessDefinitions() throws Exception {
        long process = process(tenant, form(tenant, marker, "ENABLED"), nodes());
        for (String invalid : List.of(nodes() + " []", nodes().replace("\"Start\"", "\"Start\",\"nodeName\":\"Duplicate\""),
                "[]", " ".repeat(65537) + nodes())) {
            jdbc.update("UPDATE approval_process SET node_json=? WHERE id=?", invalid, process);
            assertThatThrownBy(() -> service.readProcessDefinitionAgent(actor.id(), tenant, process)).isInstanceOf(BusinessException.class);
            get(actor, "formKey=" + marker, 400);
        }
    }
    private long form(long owner, String key, String status) {
        return jdbc.queryForObject("INSERT INTO approval_form(tenant_id,form_key,form_name,schema_json,status,created_by) VALUES (?,?,?,? ,?,?) RETURNING id", Long.class,
                owner, key, key, "{\"fields\":[{\"name\":\"reason\",\"label\":\"Reason\",\"type\":\"textarea\",\"required\":true}]}", status, actor.id());
    }
    private long process(long owner, long form, String nodes) {
        return jdbc.queryForObject("INSERT INTO approval_process(tenant_id,process_key,process_name,form_id,node_json,status,created_by) VALUES (?,?,?,?,?,'ENABLED',?) RETURNING id", Long.class,
                owner, marker + "_process", "Synthetic process", form, nodes, actor.id());
    }
    private String nodes() {
        return "[{\"nodeType\":\"START\",\"nodeName\":\"Start\"},{\"nodeType\":\"APPROVAL\",\"nodeName\":\"Review\",\"approveType\":\"USER\",\"targetKey\":\""
                + actor.id() + "\",\"mode\":\"SEQUENTIAL\"},{\"nodeType\":\"END\",\"nodeName\":\"End\"}]";
    }
    private JsonNode get(AgentHttpTestFixture.Actor identity, String query, int expected) throws Exception {
        var response = AgentHttpTestFixture.client().send(http.request("GET", "/api/approval-config/application-templates?" + query, identity, null, null).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(expected);
        return json.readTree(response.body()).path("data");
    }
}
