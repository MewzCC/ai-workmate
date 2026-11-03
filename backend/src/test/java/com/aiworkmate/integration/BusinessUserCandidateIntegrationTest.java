package com.aiworkmate.integration;

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
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BusinessUserCandidateIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    private AgentHttpTestFixture http;
    private long tenant;
    private AgentHttpTestFixture.Actor visitor, manager;
    private String marker;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        AgentBrowserFixture.registerLocal(properties, ENVIRONMENT, AgentBrowserFixture.reserveUiPort(), SECRET);
    }
    @BeforeEach void setup() throws Exception {
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        http = new AgentHttpTestFixture(jdbc, json, passwords, port, "zh-CN");
        visitor = http.createAndLogin(tenant, List.of("visitor:create"));
        manager = http.createAndLogin(tenant, List.of("hr:manage"));
        marker = "Candidate-" + UUID.randomUUID();
    }
    @Test void visitorReadsOnlyMinimalActiveTenantCandidates() throws Exception {
        long active = person(tenant, marker, 1); person(tenant, marker, 0);
        long foreign = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Synthetic other tenant') RETURNING id", Long.class, UUID.randomUUID().toString());
        person(foreign, marker, 1);
        JsonNode result = data(host(visitor, "keyword=" + marker), 200);
        assertThat(result.path("total").asLong()).isEqualTo(1);
        JsonNode row = result.path("records").get(0);
        Set<String> fields = new HashSet<>(); row.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "name");
        assertThat(row.path("id").asLong()).isEqualTo(active);
    }
    @Test void hostPaginationLoads205MatchesWithStableDistinctIds() throws Exception {
        for (int index = 0; index < 205; index++) person(tenant, marker, 1);
        Set<Long> ids = new HashSet<>(); long previous = 0;
        for (int page = 1; page <= 3; page++) {
            JsonNode result = data(host(visitor, "keyword=" + marker + "&page=" + page + "&size=100"), 200);
            assertThat(result.path("total").asLong()).isEqualTo(205);
            assertThat(result.path("records").size()).isEqualTo(page < 3 ? 100 : 5);
            for (JsonNode row : result.path("records")) {
                long id = row.path("id").asLong(); assertThat(id).isGreaterThan(previous);
                assertThat(ids.add(id)).isTrue(); previous = id;
            }
        }
        assertThat(ids).hasSize(205);
    }
    @Test void reviewerRequiresLiveAuthorityAndExcludesTheRequester() throws Exception {
        label(manager.id(), marker);
        var reviewer = http.createAndLogin(tenant, List.of("hr:manage")); label(reviewer.id(), marker);
        var ordinary = http.createAndLogin(tenant, List.of()); label(ordinary.id(), marker);
        long disabled = person(tenant, marker, 0);
        jdbc.update("UPDATE app_user SET role='SUPER_ADMIN' WHERE id=?", disabled);
        JsonNode result = data(reviewers(manager, "keyword=" + marker), 200);
        assertThat(result.path("total").asLong()).isEqualTo(1);
        assertThat(result.path("records").get(0).path("id").asLong()).isEqualTo(reviewer.id());
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='hr:manage'", tenant, reviewer.role());
        assertThat(data(reviewers(manager, "keyword=" + marker), 200).path("total").asLong()).isZero();
    }
    @Test void assignedRolesOverrideAnUnassignedPrivilegedLegacyRole() throws Exception {
        var ordinary = http.createAndLogin(tenant, List.of()); label(ordinary.id(), marker);
        jdbc.update("UPDATE app_user SET role='SUPER_ADMIN' WHERE id=?", ordinary.id());
        assertThat(data(reviewers(manager, "keyword=" + marker), 200).path("total").asLong()).isZero();
        jdbc.update("DELETE FROM user_role WHERE tenant_id=? AND user_id=?", tenant, ordinary.id());
        JsonNode result = data(reviewers(manager, "keyword=" + marker), 200);
        assertThat(result.path("total").asLong()).isEqualTo(1);
        assertThat(result.path("records").get(0).path("id").asLong()).isEqualTo(ordinary.id());
    }
    @Test void preservesAmbiguousReviewerNames() throws Exception {
        for (int index = 0; index < 2; index++) {
            var reviewer = http.createAndLogin(tenant, List.of("hr:manage")); label(reviewer.id(), marker);
        }
        JsonNode result = data(reviewers(manager, "keyword=" + marker), 200);
        assertThat(result.path("total").asLong()).isEqualTo(2);
        assertThat(result.path("records").size()).isEqualTo(2);
    }
    @Test void domainAuthorityIsNotInterchangeableAndRevocationUsesTheSameCookie() throws Exception {
        data(host(manager, ""), 403); data(reviewers(visitor, ""), 403);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=?", tenant, visitor.role());
        data(host(visitor, ""), 403);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=?", tenant, manager.role());
        data(reviewers(manager, ""), 403);
    }
    @Test void refusesInvalidPagingAndOverlongNames() throws Exception {
        for (String query : List.of("page=0", "page=10001", "size=101", "keyword=" + "x".repeat(121))) {
            data(host(visitor, query), 400); data(reviewers(manager, query), 400);
        }
    }
    private long person(long ownerTenant, String label, int status) {
        String suffix = UUID.randomUUID().toString();
        return jdbc.queryForObject("INSERT INTO app_user(tenant_id,username,password,email,display_name,status) VALUES (?,?,'unusable synthetic password',?,?,?) RETURNING id",
                Long.class, ownerTenant, suffix, suffix + "@example.invalid", label, status);
    }
    private void label(long userId, String label) { jdbc.update("UPDATE app_user SET display_name=? WHERE id=?", label, userId); }
    private HttpResponse<String> host(AgentHttpTestFixture.Actor actor, String query) throws Exception {
        return get(actor, "/api/admin-assets/visitor-bookings/host-options?" + query);
    }
    private HttpResponse<String> reviewers(AgentHttpTestFixture.Actor actor, String query) throws Exception {
        return get(actor, "/api/hr/employee-changes/reviewer-options?" + query);
    }
    private HttpResponse<String> get(AgentHttpTestFixture.Actor actor, String path) throws Exception {
        return AgentHttpTestFixture.client().send(http.request("GET", path, actor, null, null).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode data(HttpResponse<String> response, int expected) throws Exception {
        assertThat(response.statusCode()).isEqualTo(expected);
        return json.readTree(response.body()).path("data");
    }
}
