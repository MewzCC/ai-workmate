package com.aiworkmate.integration;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.ApprovalDraftRequest;
import com.aiworkmate.service.GenericApprovalService;
import com.aiworkmate.service.ExpenseApplicationService;
import com.aiworkmate.service.model.ExpenseAgentDraftCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real domain transactions only. New tool registration and write release gates are a later batch. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AtomicApprovalDomainIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired GenericApprovalService approvals;
    @Autowired ExpenseApplicationService expenses;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor owner;
    private long tenant;
    private String formKey;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        AgentBrowserFixture.registerLocal(properties, ENVIRONMENT, AgentBrowserFixture.reserveUiPort(), SECRET);
    }
    @BeforeEach void setup() throws Exception {
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(ENVIRONMENT.schema());
        http = new AgentHttpTestFixture(jdbc, json, passwords, port, "zh-CN");
        owner = http.createAndLogin(tenant, List.of("route:approval-start", "route:expense", "approval:create", "approval:submit"));
        long approver = LeaveWriteFixture.linkedApprover(jdbc, tenant, owner.id());
        formKey = "atomic_" + UUID.randomUUID().toString().replace("-", "");
        long form = jdbc.queryForObject("INSERT INTO approval_form(tenant_id,form_key,form_name,schema_json,status,created_by) VALUES (?,?,?,?,'ENABLED',?) RETURNING id", Long.class,
                tenant, formKey, formKey, "{\"fields\":[{\"name\":\"amount\",\"label\":\"Amount\",\"type\":\"money\",\"required\":true,\"min\":0.01},{\"name\":\"reason\",\"label\":\"Reason\",\"type\":\"textarea\",\"required\":true}]}", owner.id());
        String nodes = "[{\"nodeType\":\"START\",\"nodeName\":\"Start\"},{\"nodeType\":\"APPROVAL\",\"nodeName\":\"Review\",\"approveType\":\"USER\",\"targetKey\":\""
                + approver + "\",\"mode\":\"SEQUENTIAL\"},{\"nodeType\":\"END\",\"nodeName\":\"End\"}]";
        jdbc.update("INSERT INTO approval_process(tenant_id,process_key,process_name,form_id,node_json,status,created_by) VALUES (?,?,?,?,?,'ENABLED',?)", tenant, formKey, formKey, form, nodes, owner.id());
    }
    @AfterEach void cleanup() {
        jdbc.execute("DROP TRIGGER IF EXISTS atomic_domain_audit_fault ON business_audit_log");
        jdbc.execute("DROP FUNCTION IF EXISTS atomic_domain_audit_fault()");
    }
    @ParameterizedTest @ValueSource(booleans = { false, true })
    void createsOnePendingApplicationWithFrozenDefinitionsAndReplaysOneReceipt(boolean expense) {
        String key = key(); long id = apply(expense, key, "12.50", tenant);
        assertThat(apply(expense, key, "12.500", tenant)).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT status FROM approval_application WHERE id=?", String.class, id)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT version FROM approval_application WHERE id=?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT form_schema_snapshot IS NOT NULL AND process_node_snapshot IS NOT NULL FROM approval_application WHERE id=?", Boolean.class, id)).isTrue();
        assertOneApplication(key, id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE actor_user_id=? AND resource_type='GENERIC_APPROVAL' AND result='SUCCESS'", Long.class, owner.id())).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(booleans = { false, true })
    void rejectsChangedArgumentsWithoutDuplicatingTheWorkflow(boolean expense) {
        String key = key(); long id = apply(expense, key, "12.50", tenant);
        assertThatThrownBy(() -> apply(expense, key, "12.51", tenant))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        assertOneApplication(key, id);
    }
    @ParameterizedTest @ValueSource(booleans = { false, true })
    void submissionAuditFailureRollsBackDraftWorkflowAndPriorAudit(boolean expense) {
        jdbc.execute("CREATE FUNCTION atomic_domain_audit_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.resource_type='GENERIC_APPROVAL' AND NEW.action='SUBMIT' AND NEW.result='SUCCESS' THEN RAISE EXCEPTION 'synthetic atomic audit fault'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER atomic_domain_audit_fault BEFORE INSERT ON business_audit_log FOR EACH ROW EXECUTE FUNCTION atomic_domain_audit_fault()");
        String key = key();
        assertThatThrownBy(() -> apply(expense, key, "12.50", tenant)).isInstanceOf(RuntimeException.class);
        assertEmpty(key);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE actor_user_id=? AND resource_type='GENERIC_APPROVAL' AND result='SUCCESS'", Long.class, owner.id())).isZero();
    }
    @ParameterizedTest @ValueSource(booleans = { false, true })
    void rechecksSubmitAuthorityBeforeCreatingAnyDraft(boolean expense) {
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='approval:submit'", tenant, owner.role());
        String key = key();
        assertThatThrownBy(() -> apply(expense, key, "12.50", tenant))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getStatus().value()).isEqualTo(403));
        assertEmpty(key);
    }
    @ParameterizedTest @ValueSource(booleans = { false, true })
    void rejectsAnExpectedTenantMismatchBeforeAnyBusinessWrite(boolean expense) {
        String key = key();
        assertThatThrownBy(() -> apply(expense, key, "12.50", tenant + 1000))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getStatus().value()).isEqualTo(403));
        assertEmpty(key);
    }
    @ParameterizedTest @ValueSource(booleans = { false, true })
    void simultaneousCallsWithTheSameKeyHaveOneCommittedEffect(boolean expense) throws Exception {
        String key = key(); var barrier = new CyclicBarrier(2); var pool = Executors.newFixedThreadPool(2);
        try {
            var left = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return apply(expense, key, "12.50", tenant); });
            var right = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return apply(expense, key, "12.50", tenant); });
            long id = left.get(25, TimeUnit.SECONDS);
            assertThat(right.get(25, TimeUnit.SECONDS)).isEqualTo(id);
            assertOneApplication(key, id);
        } finally { pool.shutdownNow(); }
    }
    @Test void preservesExactDecimalsAndRejectsNearbyChangedValuesOnReplay() throws Exception {
        String key = key(); long id = apply(false, key, "1000000000000000.01", tenant);
        String stored = jdbc.queryForObject("SELECT data_json FROM approval_application WHERE id=?", String.class, id);
        assertThat(json.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readTree(stored).path("amount").decimalValue()).isEqualByComparingTo("1000000000000000.01");
        assertThatThrownBy(() -> apply(false, key, "1000000000000000.02", tenant)).isInstanceOf(BusinessException.class);
        assertOneApplication(key, id);
    }
    @Test void rejectsIncompleteAtomicRequestsWithoutLeavingDrafts() {
        String key = key();
        assertThatThrownBy(() -> approvals.applyAgentApplication(owner.id(), tenant, new ApprovalDraftRequest(formKey, formKey, Map.of("amount", new BigDecimal("12.50"))), key))
                .isInstanceOf(BusinessException.class);
        assertEmpty(key);
        assertThatThrownBy(() -> expenses.applyAgentExpense(owner.id(), tenant, new ExpenseAgentDraftCommand(new BigDecimal("12.50"), "TRAVEL", LocalDate.now(), null, null), key))
                .isInstanceOf(BusinessException.class);
        assertEmpty(key);
    }
    @Test void rejectsNonNumericMoneyAndAppliesAnExactDecimalMinimumOverHttp() throws Exception {
        String schema = "{\"fields\":[{\"name\":\"amount\",\"label\":\"Amount\",\"type\":\"money\",\"required\":true,\"min\":1.00000000000000002},{\"name\":\"reason\",\"label\":\"Reason\",\"type\":\"textarea\",\"required\":true}]}";
        jdbc.update("UPDATE approval_form SET schema_json=? WHERE tenant_id=? AND form_key=?", schema, tenant, formKey);
        draft("\"not a number\"", 400);
        draft("1.00000000000000001", 400);
        var response = draft("1.00000000000000002", 200);
        long id = json.readTree(response.body()).path("data").path("id").asLong();
        assertThat(jdbc.queryForObject("SELECT data_json FROM approval_application WHERE id=?", String.class, id)).contains("1.00000000000000002");
    }
    private long apply(boolean expense, String operationKey, String amount, long expectedTenant) {
        if (expense) return expenses.applyAgentExpense(owner.id(), expectedTenant,
                new ExpenseAgentDraftCommand(new BigDecimal(amount), "TRAVEL", LocalDate.now(), "SYNTHETIC-INVOICE", "Synthetic reason"), operationKey).applicationId();
        return approvals.applyAgentApplication(owner.id(), expectedTenant,
                new ApprovalDraftRequest(formKey, formKey, Map.of("amount", new BigDecimal(amount), "reason", "Synthetic reason")), operationKey).id();
    }
    private HttpResponse<String> draft(String amountJson, int status) throws Exception {
        String body = "{\"formKey\":\"" + formKey + "\",\"formData\":{\"amount\":" + amountJson + ",\"reason\":\"Synthetic reason\"},\"processKey\":\"" + formKey + "\"}";
        var response = AgentHttpTestFixture.client().send(http.request("POST", "/api/approval-applications/drafts", owner, null, body).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(status); return response;
    }
    private void assertEmpty(String key) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_application WHERE tenant_id=? AND applicant_user_id=? AND agent_operation_key=?", Long.class, tenant, owner.id(), key)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_instance WHERE tenant_id=? AND applicant_id=? AND business_type='GENERIC_APPROVAL'", Long.class, tenant, owner.id())).isZero();
    }
    private void assertOneApplication(String key, long id) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_application WHERE tenant_id=? AND applicant_user_id=? AND agent_operation_key=?", Long.class, tenant, owner.id(), key)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_instance WHERE tenant_id=? AND applicant_id=? AND business_type='GENERIC_APPROVAL' AND business_id=?", Long.class, tenant, owner.id(), id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE tenant_id=? AND business_type='GENERIC_APPROVAL' AND business_id=? AND status='PENDING'", Long.class, tenant, id)).isEqualTo(1);
    }
    private String key() { return "atomic-domain-" + UUID.randomUUID(); }
}
