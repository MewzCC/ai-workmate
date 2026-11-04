package com.aiworkmate.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiworkmate.dto.AssetLedgerRequest;
import com.aiworkmate.dto.AssetOperationRequest;
import com.aiworkmate.dto.AssetInventoryRequest;
import com.aiworkmate.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Domain-scoped candidate endpoints with real Cookie authentication and PostgreSQL paging. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AssetAssignmentCandidateIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private PasswordEncoder passwords;
    @Autowired private com.aiworkmate.service.AdminAssetsService assets;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor writer;
    private long tenant, department;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        AgentBrowserFixture.registerLocal(properties, ENVIRONMENT, AgentBrowserFixture.reserveUiPort(), SECRET);
    }

    @BeforeEach void setup() throws Exception {
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        http = new AgentHttpTestFixture(jdbc, json, passwords, port, "zh-CN");
        writer = http.createAndLogin(tenant, List.of("asset:write"));
        department = department(tenant, 1);
    }

    @Test void assetOnlyWriterReadsMinimalActiveCandidatesWithoutHrOrAccessManagement() throws Exception {
        long otherDepartment = department(tenant, 1);
        long disabledDepartment = department(tenant, 0);
        long eligible = person(department, 1);
        person(department, 0); person(otherDepartment, 1);
        JsonNode departments = data(get("departments", writer), 200);
        Set<Long> departmentIds = new HashSet<>();
        departments.forEach(row -> departmentIds.add(row.path("id").asLong()));
        assertThat(departmentIds).contains(department, otherDepartment).doesNotContain(disabledDepartment);
        JsonNode result = data(get("custodians?departmentId=" + department, writer), 200);
        assertThat(result.path("total").asLong()).isEqualTo(1);
        JsonNode row = result.path("records").get(0);
        Set<String> fields = new HashSet<>(); row.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "name", "departmentId");
        assertThat(row.path("id").asLong()).isEqualTo(eligible);
        assertThat(row.path("departmentId").asLong()).isEqualTo(department);
        assertThat(row.path("name").asText()).isEqualTo("Synthetic candidate");
    }

    @Test void pagedDepartmentLookupFiltersAtTheDatabaseAndKeepsDisabledDepartmentsOut() {
        String name = "Search-" + UUID.randomUUID();
        jdbc.update("UPDATE department SET name=? WHERE id=?", name, department);
        long inactive = department(tenant, 0);
        jdbc.update("UPDATE department SET name=? WHERE id=?", name, inactive);
        var result = assets.assetDepartmentOptions(writer.id(), name, 1, 1);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).singleElement().satisfies(row -> assertThat(row.id()).isEqualTo(department));
        assertThat(assets.assetDepartmentOptions(writer.id(), "' OR 1=1 --", 1, 20).records()).isEmpty();
    }

    @Test void nameLookupPreservesAmbiguousMatchesInsteadOfSelectingOnePerson() {
        person(department, 1); person(department, 1); person(department, 0);
        person(department(tenant, 1), 1);
        var result = assets.assetCustodianOptions(writer.id(), department, "Synthetic candidate", 1, 20);
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.records()).hasSize(2).allSatisfy(row -> assertThat(row.departmentId()).isEqualTo(department));
        assertThat(assets.assetCustodianOptions(writer.id(), department, "no-such-candidate", 1, 20).records()).isEmpty();
    }

    @Test void pagesThroughAll205CandidatesWithStableOrderingAndNoDuplicates() throws Exception {
        for (int index = 0; index < 205; index++) person(department, 1);
        Set<Long> ids = new HashSet<>(); long previous = 0;
        for (int page = 1; page <= 3; page++) {
            JsonNode result = data(get("custodians?departmentId=" + department + "&size=100&page=" + page, writer), 200);
            assertThat(result.path("total").asLong()).isEqualTo(205);
            assertThat(result.path("records").size()).isEqualTo(page < 3 ? 100 : 5);
            for (JsonNode row : result.path("records")) {
                long id = row.path("id").asLong();
                assertThat(id).isGreaterThan(previous); assertThat(ids.add(id)).isTrue(); previous = id;
            }
        }
        assertThat(ids).hasSize(205);
    }

    @Test void hrManagementDoesNotGrantAssetAssignmentAccess() throws Exception {
        var hr = http.createAndLogin(tenant, List.of("hr:manage", "access:manage"));
        data(get("departments", hr), 403);
        data(get("custodians?departmentId=" + department, hr), 403);
    }

    @Test void rejectsForeignTenantDepartmentEvenForAnAssetWriter() throws Exception {
        long otherTenant = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Synthetic foreign tenant') RETURNING id",
                Long.class, "CANDIDATE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        long foreign = department(otherTenant, 1);
        data(get("custodians?departmentId=" + foreign, writer), 404);
    }

    @Test void agentQueriesBindTrustedTenantBeforeReadingCandidates() {
        long candidate = person(department, 1);
        assertThat(assets.assetCustodianOptions(writer.id(), tenant, department, "Synthetic candidate", 1, 20).records())
                .singleElement().satisfies(row -> assertThat(row.id()).isEqualTo(candidate));
        for (Runnable query : List.<Runnable>of(
                () -> assets.assetDepartmentOptions(writer.id(), tenant + 1, null, 1, 20),
                () -> assets.assetCustodianOptions(writer.id(), tenant + 1, department, null, 1, 20))) {
            assertThatThrownBy(query::run).isInstanceOfSatisfying(BusinessException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(com.aiworkmate.common.ErrorCode.RESOURCE_FORBIDDEN.getErrorCode()));
        }
    }

    @Test void rejectsCustodianLookupInADisabledDepartment() throws Exception {
        long disabled = department(tenant, 0);
        person(disabled, 1);
        data(get("custodians?departmentId=" + disabled, writer), 404);
    }

    @Test void rechecksRevokedAuthorityWithTheSameLoginCookie() throws Exception {
        data(get("departments", writer), 200);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='asset:write'", tenant, writer.role());
        data(get("departments", writer), 403);
        data(get("custodians?departmentId=" + department, writer), 403);
    }

    @ParameterizedTest
    @ValueSource(strings = { "&page=0", "&size=101", "&page=10001" })
    void rejectsOutOfBudgetPaging(String suffix) throws Exception {
        data(get("custodians?departmentId=" + department + suffix, writer), 400);
    }

    @Test void disabledDepartmentCannotBeAssignedAtAssetCreation() {
        long disabled = department(tenant, 0);
        String code = UUID.randomUUID().toString();
        assertThatThrownBy(() -> assets.createAsset(writer.id(), new AssetLedgerRequest(code, "Synthetic asset", "TEST", null,
                "IDLE", disabled, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asset_ledger WHERE tenant_id=? AND asset_code=?", Long.class, tenant, code)).isZero();
    }

    @Test void disabledDepartmentCannotReceiveAClaim() {
        var asset = createAsset(); long disabled = department(tenant, 0); long owner = person(disabled, 1);
        String before = assetSnapshot(asset.id());
        assertThatThrownBy(() -> assets.claimAsset(writer.id(), asset.id(), new AssetOperationRequest(asset.version(), owner, disabled, "Synthetic claim")))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(assetSnapshot(asset.id())).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void disabledDepartmentCannotReceiveIdleOrInUseTransfers(boolean inUse) {
        var asset = createAsset();
        if (inUse) asset = assets.claimAsset(writer.id(), asset.id(), new AssetOperationRequest(asset.version(), person(department, 1), department, "Synthetic initial claim"));
        long disabled = department(tenant, 0); long owner = person(disabled, 1);
        long id = asset.id(); int version = asset.version(); String before = assetSnapshot(id);
        assertThatThrownBy(() -> assets.transferAsset(writer.id(), id, new AssetOperationRequest(version, owner, disabled, "Synthetic transfer")))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(assetSnapshot(id)).isEqualTo(before);
    }

    @Test void agentDomainClaimAlsoRejectsADisabledEmployeeDepartment() throws Exception {
        var asset = createAsset(); long disabled = department(tenant, 0); long owner = person(disabled, 1);
        var claimant = http.createAndLogin(tenant, List.of("asset:claim"));
        String before = assetSnapshot(asset.id());
        assertThatThrownBy(() -> assets.claimAssetAgent(claimant.id(),
                new com.aiworkmate.service.model.AssetAgentClaimCommand(asset.id(), owner, asset.version(), "Synthetic agent claim"), "disabled-" + UUID.randomUUID()))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(assetSnapshot(asset.id())).isEqualTo(before);
    }

    @Test void inventoryMayRecordAnExistingInactiveLocationWithoutAssigningTheAssetThere() throws Exception {
        var asset = createAsset(); long disabled = department(tenant, 0); long owner = person(disabled, 1);
        var result = assets.inventoryAsset(writer.id(), asset.id(), new AssetInventoryRequest(asset.version(),
                "LOCATION_MISMATCH", "IN_USE", disabled, owner, "Synthetic physical location discrepancy"));
        assertThat(result.departmentId()).isEqualTo(department); assertThat(result.status()).isEqualTo("IDLE");
        assertThat(result.ownerUserId()).isNull();
    }

    private com.aiworkmate.dto.AssetLedgerResponse createAsset() {
        return assets.createAsset(writer.id(), new AssetLedgerRequest(UUID.randomUUID().toString(), "Synthetic asset", "TEST", null,
                "IDLE", department, null, null, null, null, null));
    }

    private String assetSnapshot(long id) {
        return jdbc.queryForObject("SELECT to_jsonb(asset)::text FROM asset_ledger asset WHERE id=?", String.class, id)
                + jdbc.queryForObject("SELECT count(*) FROM asset_operation WHERE asset_id=?", Long.class, id);
    }

    private HttpResponse<String> get(String suffix, AgentHttpTestFixture.Actor actor) throws Exception {
        return AgentHttpTestFixture.client().send(http.request("GET", "/api/admin-assets/assets/assignment/" + suffix,
                actor, null, null).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode data(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        return json.readTree(response.body()).path("data");
    }
    private long department(long tenantId, int status) {
        return jdbc.queryForObject("INSERT INTO department(tenant_id,code,name,status) VALUES (?,?,'Synthetic department',?) RETURNING id",
                Long.class, tenantId, UUID.randomUUID().toString(), status);
    }
    private long person(long departmentId, int status) {
        String suffix = UUID.randomUUID().toString();
        return jdbc.queryForObject("INSERT INTO app_user(tenant_id,username,password,email,display_name,department_id,status) VALUES (?,?,'unusable synthetic password',?,'Synthetic candidate',?,?) RETURNING id",
                Long.class, tenant, suffix, suffix + "@example.invalid", departmentId, status);
    }
}
