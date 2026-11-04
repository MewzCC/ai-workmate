package com.aiworkmate.integration;

import com.aiworkmate.agent.capability.PageCapabilityCatalog;
import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.dto.ApprovalDraftRequest;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.service.GenericApprovalService;
import com.aiworkmate.service.ExpenseApplicationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.agent.registry.ToolRegistry;
import com.aiworkmate.agent.registry.ToolPolicySource;
import com.aiworkmate.service.model.ExpenseAgentDraftCommand;
import com.aiworkmate.integration.AgentHttpTestFixture.Actor;
import com.aiworkmate.integration.FrozenWriteTaskFixture.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** Real confirmation, Gateway and generic-approval transactions in an owned disposable environment. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="AGENT_TEST_DB_URL",matches="jdbc:postgresql:.*")
class AgentApprovalWriteIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private PasswordEncoder passwords;
    @Autowired private ToolCatalog catalog;
    @Autowired private PageCapabilityCatalog pages;
    @Autowired private AgentHashing hashing;
    @Autowired private AgentTaskWorker worker;
    @Autowired private GenericApprovalService approvals;
    @Autowired private ExpenseApplicationService expenses;
    @Autowired private UserAccessService access;
    @Autowired private ToolRegistry registry;
    @Autowired private ToolPolicySource policies;
    private AgentHttpTestFixture http;
    private long tenant, application, approver;
    private int version;
    private Actor owner;
    private String initialStatus, formKey;
    private static final String SECRET = UUID.randomUUID().toString()+UUID.randomUUID();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        ENVIRONMENT.register(p);
        p.add("server.address",()->"127.0.0.1");
        p.add("jwt.secret",()->SECRET);
        p.add("app.auth.cookie-name",()->"oa_approval_write_it");
        p.add("app.auth.cookie-secure",()->"false");
        p.add("spring.ai.openai.api-key",()->"development-only-unconfigured");
        p.add("agent.enabled",()->"true");
        p.add("agent.execution-enabled",()->"true");
        p.add("agent.write-tools-enabled",()->"true");
        p.add("agent.worker.poll-delay-ms",()->"3600000");
        p.add("agent.worker.heartbeat-delay-ms",()->"3600000");
    }
    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo(ENVIRONMENT.schema());
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'",Long.class);
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=true WHERE tenant_id=?",tenant);
        http = new AgentHttpTestFixture(jdbc,json,passwords,port,"en-US");
    }
    @AfterEach void finish() {
        if (owner!=null) {
            jdbc.update("UPDATE agent_task SET status='CANCELLED' WHERE user_id=? AND status IN ('WAITING_CONFIRMATION','PLAN_READY','QUEUED')",owner.id());
            await().atMost(Duration.ofSeconds(20)).untilAsserted(()->assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE user_id=? AND status='RUNNING'",Long.class,owner.id())).isZero());
        }
        jdbc.execute("DROP TRIGGER IF EXISTS approval_write_fault ON business_audit_log");
        jdbc.execute("DROP FUNCTION IF EXISTS approval_write_fault()");
    }
    static Stream<ToolCode> writes() { return Stream.of(ToolCode.values()).filter(c->c.isWrite() && (c.code().startsWith("approval.application.") || c==ToolCode.EXPENSE_APPLY)); }
    static Stream<ToolCode> atomicCreations() { return Stream.of(ToolCode.APPROVAL_APPLICATION_APPLY,ToolCode.EXPENSE_APPLY); }
    private static boolean atomicCreation(ToolCode code) { return code==ToolCode.APPROVAL_APPLICATION_APPLY || code==ToolCode.EXPENSE_APPLY; }
    static Stream<ToolCode> versionedWrites() { return writes().filter(c->c!=ToolCode.APPROVAL_APPLICATION_CREATE_DRAFT && !atomicCreation(c)); }

    @ParameterizedTest @MethodSource("writes")
    void confirmedWriteReturnsTheActualStateAndCommitsItsAuditTogether(ToolCode code) throws Exception {
        var task = prepare(code);
        installTransactionProbe();
        String token = confirm(task);
        assertThat(execute(task,token).statusCode()).isEqualTo(202);
        terminal(task,"SUCCEEDED");
        var result = json.readTree(response("GET",path(task),owner,null).body()).path("data").path("steps").get(0);
        long id = result.path("result").path("applicationId").asLong();
        String expected = switch(code) {
            case APPROVAL_APPLICATION_CREATE_DRAFT,APPROVAL_APPLICATION_UPDATE_DRAFT,APPROVAL_APPLICATION_REOPEN -> "DRAFT";
            case APPROVAL_APPLICATION_CANCEL_DRAFT -> "CANCELLED";
            case APPROVAL_APPLICATION_WITHDRAW -> "WITHDRAWN";
            default -> "PENDING";
        };
        assertThat(result.path("result").path("status").asText()).isEqualTo(expected);
        assertThat(result.path("result").path("version").asInt()).isEqualTo(code==ToolCode.APPROVAL_APPLICATION_CREATE_DRAFT?0:version+1);
        assertThat(result.path("resultSummary").asText()).contains("#"+id).doesNotContain("applicationId:");
        assertThat(result.path("result").path("formKey").asText()).isEqualTo(formKey);
        if(code!=ToolCode.EXPENSE_APPLY) assertThat(result.path("resultSummary").asText()).contains(formKey);
        assertThat(jdbc.queryForObject("SELECT status FROM approval_application WHERE id=? AND tenant_id=? AND applicant_user_id=?",String.class,id,tenant,owner.id())).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_write_probe WHERE application_id=? AND kind='AUDIT'",Long.class,id)).isEqualTo(atomicCreation(code)?2:1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_write_probe WHERE application_id=? AND kind='BUSINESS'",Long.class,id)).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT transaction_id) FROM approval_write_probe WHERE application_id=?",Long.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked AND outcome='SUCCEEDED'",Long.class,task.id())).isEqualTo(1);
        if(code==ToolCode.APPROVAL_APPLICATION_CREATE_DRAFT || code==ToolCode.APPROVAL_APPLICATION_UPDATE_DRAFT || atomicCreation(code))
            assertThat(jdbc.queryForObject("SELECT data_json::jsonb->>'reason' FROM approval_application WHERE id=?",String.class,id)).isEqualTo("controlled generic value");
        if(code==ToolCode.APPROVAL_APPLICATION_SUBMIT_DRAFT || atomicCreation(code)) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_application WHERE id=? AND form_schema_snapshot IS NOT NULL AND process_node_snapshot IS NOT NULL AND rule_snapshot IS NOT NULL",Long.class,id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE business_type='GENERIC_APPROVAL' AND business_id=? AND assignee_user_id=? AND status='PENDING'",Long.class,id,approver)).isEqualTo(1);
        }
        if(code==ToolCode.APPROVAL_APPLICATION_WITHDRAW || code==ToolCode.APPROVAL_APPLICATION_REOPEN)
            assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_instance WHERE business_type='GENERIC_APPROVAL' AND business_id=? AND status='CANCELLED'",Long.class,id)).isEqualTo(1);
        if(code==ToolCode.APPROVAL_APPLICATION_REMIND)
            assertThat(jdbc.queryForObject("SELECT reminder_count FROM workflow_task WHERE business_type='GENERIC_APPROVAL' AND business_id=? AND status='PENDING'",Integer.class,id)).isEqualTo(1);
    }
    @ParameterizedTest @MethodSource("writes")
    void missingConfirmationNeverInvokesTheHandler(ToolCode code) throws Exception {
        var task=prepare(code);
        assertThat(execute(task,null).statusCode()).isEqualTo(409);
        unchanged(task);
    }
    @ParameterizedTest @MethodSource("writes")
    void revokedToolPermissionRejectsAValidConfirmation(ToolCode code) throws Exception {
        var task=prepare(code); String token=confirm(task);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code=?",tenant,owner.role(),"agent:tool:"+code.code());
        assertThat(execute(task,token).statusCode()).isEqualTo(403);
        unchanged(task);
    }
    @ParameterizedTest @MethodSource("writes")
    void revokedDomainEntryRejectsAValidConfirmationBeforeTheHandler(ToolCode code) throws Exception {
        var task=prepare(code); String token=confirm(task);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='route:approval-start'",tenant,owner.role());
        assertThat(execute(task,token).statusCode()).isEqualTo(403);
        unchanged(task);
    }
    @ParameterizedTest @MethodSource("versionedWrites")
    void staleVersionCannotChangeTheBusinessRecord(ToolCode code) throws Exception {
        var task=prepare(code);
        jdbc.update("UPDATE approval_application SET version=version+1 WHERE id=?",application);
        assertThat(execute(task,confirm(task)).statusCode()).isEqualTo(202);
        terminal(task,"FAILED");
        assertThat(jdbc.queryForObject("SELECT status FROM approval_application WHERE id=?",String.class,application)).isEqualTo(initialStatus);
        assertThat(jdbc.queryForObject("SELECT version FROM approval_application WHERE id=?",Integer.class,application)).isEqualTo(version+1);
    }
    @ParameterizedTest @MethodSource("versionedWrites")
    void anotherApplicantsRecordIsRejectedInsideTheDomain(ToolCode code) throws Exception {
        var task=prepare(code);
        var other=http.createAndLogin(tenant,List.of());
        jdbc.update("UPDATE approval_application SET applicant_user_id=? WHERE id=?",other.id(),application);
        assertThat(execute(task,confirm(task)).statusCode()).isEqualTo(202);
        terminal(task,"FAILED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo("TOOL_RESOURCE_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT version FROM approval_application WHERE id=?",Integer.class,application)).isEqualTo(version);
    }
    @ParameterizedTest @MethodSource("versionedWrites")
    void aDifferentTenantCannotOperateOnTheOriginalApplication(ToolCode code) throws Exception {
        var original=prepare(code);
        var permissions=jdbc.queryForList("SELECT permission_code FROM rbac_role_permission WHERE tenant_id=? AND role_code=?",String.class,tenant,owner.role());
        long otherTenant=jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'isolated generic applicant') RETURNING id",Long.class,"GEN_"+UUID.randomUUID().toString().replace("-",""));
        var stranger=http.createAndLogin(otherTenant,permissions);
        jdbc.update("INSERT INTO agent_tenant_policy(tenant_id,enabled,write_tools_enabled) VALUES (?,true,true)",otherTenant);
        var args=json.readTree(jdbc.queryForObject("SELECT args::text FROM agent_task_step WHERE task_id=?",String.class,original.id()));
        String source=jdbc.queryForObject("SELECT page_id FROM agent_task WHERE id=?",String.class,original.id());
        var task=new FrozenWriteTaskFixture(jdbc,json,hashing,catalog).create(otherTenant,stranger.id(),source,code.code(),args);
        assertThat(execute(task,confirm(task,stranger),stranger).statusCode()).isEqualTo(202);
        terminal(task,"FAILED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo("TOOL_RESOURCE_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT version FROM approval_application WHERE id=?",Integer.class,application)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_application WHERE id=? AND tenant_id=? AND applicant_user_id=? AND status=?",Long.class,application,tenant,owner.id(),initialStatus)).isEqualTo(1);
    }
    @Test void reopenThenResubmitKeepsTheOldWorkflowAndCreatesExactlyOneNewInstance() throws Exception {
        var reopened=prepare(ToolCode.APPROVAL_APPLICATION_REOPEN);
        long oldInstance=jdbc.queryForObject("SELECT workflow_instance_id FROM approval_application WHERE id=?",Long.class,application);
        var oldHistory=jdbc.queryForList("SELECT * FROM workflow_action_log WHERE instance_id=? ORDER BY id",oldInstance);
        assertThat(oldHistory).hasSize(2);
        assertThat(execute(reopened,confirm(reopened)).statusCode()).isEqualTo(202);
        terminal(reopened,"SUCCEEDED");
        for(String permission:List.of("approval:submit","agent:tool:approval.application.submitDraft"))
            jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,?)",tenant,owner.role(),permission);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code='approval.application.submitDraft'");
        jdbc.update("DELETE FROM agent_page_action_policy WHERE tenant_id=? AND page_id='my-applications' AND tool_code='approval.application.submitDraft'",tenant);
        var args=json.createObjectNode().put("applicationId",application).put("version",version+1);
        var submitted=new FrozenWriteTaskFixture(jdbc,json,hashing,catalog).create(tenant,owner.id(),"my-applications","approval.application.submitDraft",args);
        assertThat(execute(submitted,confirm(submitted)).statusCode()).isEqualTo(202);
        terminal(submitted,"SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT workflow_instance_id FROM approval_application WHERE id=?",Long.class,application)).isNotEqualTo(oldInstance);
        assertThat(jdbc.queryForObject("SELECT status FROM workflow_instance WHERE id=?",String.class,oldInstance)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_instance WHERE business_type='GENERIC_APPROVAL' AND business_id=?",Long.class,application)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_instance WHERE business_type='GENERIC_APPROVAL' AND business_id=? AND status='RUNNING'",Long.class,application)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_application a JOIN workflow_instance i ON a.workflow_instance_id=i.id AND a.tenant_id=i.tenant_id WHERE a.id=? AND i.status='RUNNING' AND i.business_type='GENERIC_APPROVAL' AND i.business_id=a.id AND i.applicant_id=a.applicant_user_id",Long.class,application)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT * FROM workflow_action_log WHERE instance_id=? ORDER BY id",oldInstance)).hasSize(oldHistory.size()+1).containsAll(oldHistory);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE business_type='GENERIC_APPROVAL' AND business_id=? AND status='PENDING'",Long.class,application)).isEqualTo(1);
    }
    @Test void aSecondConfirmedReminderIsRateLimitedWithoutChangingBusinessOrAudit() throws Exception {
        var first=prepare(ToolCode.APPROVAL_APPLICATION_REMIND);
        assertThat(execute(first,confirm(first)).statusCode()).isEqualTo(202);
        terminal(first,"SUCCEEDED");
        var args=json.createObjectNode().put("applicationId",application).put("version",version+1);
        var second=new FrozenWriteTaskFixture(jdbc,json,hashing,catalog).create(tenant,owner.id(),"my-applications","approval.application.remind",args);
        assertThat(execute(second,confirm(second)).statusCode()).isEqualTo(202);
        terminal(second,"FAILED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,second.id())).isEqualTo("GATEWAY_THROTTLED");
        assertThat(jdbc.queryForObject("SELECT version FROM approval_application WHERE id=?",Integer.class,application)).isEqualTo(version+1);
        assertThat(jdbc.queryForObject("SELECT reminder_count FROM workflow_task WHERE business_type='GENERIC_APPROVAL' AND business_id=? AND status='PENDING'",Integer.class,application)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE resource_type='GENERIC_APPROVAL' AND resource_id=? AND action='REMIND' AND result='SUCCESS'",Long.class,Long.toString(application))).isEqualTo(1);
    }
    @Test void businessAuditFailureRollsBackTheCreatedDraft() throws Exception {
        var task=prepare(ToolCode.APPROVAL_APPLICATION_CREATE_DRAFT);
        jdbc.execute("CREATE FUNCTION approval_write_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.resource_type='GENERIC_APPROVAL' AND NEW.result='SUCCESS' THEN RAISE EXCEPTION 'synthetic approval audit fault'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER approval_write_fault BEFORE INSERT ON business_audit_log FOR EACH ROW EXECUTE FUNCTION approval_write_fault()");
        assertThat(execute(task,confirm(task)).statusCode()).isEqualTo(202);
        terminal(task,"PARTIALLY_SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_application WHERE applicant_user_id=?",Long.class,owner.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo("TOOL_RESULT_UNKNOWN");
    }

    @ParameterizedTest @MethodSource("atomicCreations")
    void atomicSubmissionAuditFailureRollsBackTheDraftAndWorkflow(ToolCode code) throws Exception {
        var task=prepare(code);
        jdbc.execute("CREATE FUNCTION approval_write_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.resource_type='GENERIC_APPROVAL' AND NEW.action='SUBMIT' AND NEW.result='SUCCESS' THEN RAISE EXCEPTION 'synthetic submission audit fault'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER approval_write_fault BEFORE INSERT ON business_audit_log FOR EACH ROW EXECUTE FUNCTION approval_write_fault()");
        assertThat(execute(task,confirm(task)).statusCode()).isEqualTo(202);
        terminal(task,"PARTIALLY_SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_application WHERE applicant_user_id=?",Long.class,owner.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_instance WHERE applicant_id=? AND business_type='GENERIC_APPROVAL'",Long.class,owner.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE actor_user_id=? AND resource_type='GENERIC_APPROVAL' AND result='SUCCESS'",Long.class,owner.id())).isZero();
    }

    @Test void atomicDomainReplayReturnsTheSameApplicationAndRejectsChangedArguments() throws Exception {
        prepare(ToolCode.APPROVAL_APPLICATION_APPLY);
        String key="atomic-fixture-"+UUID.randomUUID();
        var request=new ApprovalDraftRequest(formKey,formKey,Map.of("reason","stable atomic value"));
        var first=approvals.applyAgentApplication(owner.id(),tenant,request,key);
        var replay=approvals.applyAgentApplication(owner.id(),tenant,request,key);
        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.version()).isEqualTo(first.version());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_application WHERE applicant_user_id=?",Long.class,owner.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_instance WHERE applicant_id=? AND business_type='GENERIC_APPROVAL'",Long.class,owner.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE actor_user_id=? AND resource_type='GENERIC_APPROVAL' AND result='SUCCESS'",Long.class,owner.id())).isEqualTo(2);
        assertThatThrownBy(()->approvals.applyAgentApplication(owner.id(),tenant,
                new ApprovalDraftRequest(formKey,formKey,Map.of("reason","changed value")),key))
                .isInstanceOfSatisfying(BusinessException.class,error->assertThat(error.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT.getErrorCode()));
    }

    @Test void atomicExpenseReplayKeepsOneWorkflowAndRejectsChangedAmount() throws Exception {
        prepare(ToolCode.EXPENSE_APPLY);
        String key="atomic-expense-"+UUID.randomUUID();
        var command=new ExpenseAgentDraftCommand(new java.math.BigDecimal("88.50"),"TRAVEL",
                java.time.LocalDate.now().minusDays(1),"SYNTHETIC-INVOICE","controlled generic value");
        var first=expenses.applyAgentExpense(owner.id(),tenant,command,key);
        var replay=expenses.applyAgentExpense(owner.id(),tenant,command,key);
        assertThat(replay).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_application WHERE applicant_user_id=? AND form_key='expense-application'",Long.class,owner.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_instance WHERE applicant_id=? AND business_type='GENERIC_APPROVAL'",Long.class,owner.id())).isEqualTo(1);
        assertThatThrownBy(()->expenses.applyAgentExpense(owner.id(),tenant,new ExpenseAgentDraftCommand(
                new java.math.BigDecimal("99.50"),command.category(),command.expenseDate(),command.invoiceNumber(),command.reason()),key))
                .isInstanceOfSatisfying(BusinessException.class,error->assertThat(error.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT.getErrorCode()));
    }

    @Test void atomicExpenseWithMissingRequiredTemplateFieldLeavesNoDraft() throws Exception {
        prepare(ToolCode.EXPENSE_APPLY);
        var command=new ExpenseAgentDraftCommand(new java.math.BigDecimal("88.50"),"TRAVEL",
                java.time.LocalDate.now().minusDays(1),null,"controlled generic value");
        assertThatThrownBy(()->expenses.applyAgentExpense(owner.id(),tenant,command,"missing-invoice-"+UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_application WHERE applicant_user_id=?",Long.class,owner.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_instance WHERE applicant_id=? AND business_type='GENERIC_APPROVAL'",Long.class,owner.id())).isZero();
    }

    private Task prepare(ToolCode code) throws Exception {
        var permissions=new LinkedHashSet<>(catalog.find(code.code()).orElseThrow().requiredPermissions());
        var routes=pages.businessPageIdsForTool(code.code()).stream().sorted().toList();
        routes.forEach(route->permissions.add("route:"+route));
        pages.prerequisitePageIdsForTool(code.code()).forEach(route->permissions.add("route:"+route));
        permissions.add("agent:tool:"+code.code());
        owner=http.createAndLogin(tenant,List.copyOf(permissions));
        approver=LeaveWriteFixture.linkedApprover(jdbc,tenant,owner.id());
        if(code==ToolCode.EXPENSE_APPLY) {
            formKey="expense-application"; version=0; application=0; initialStatus="DRAFT";
            var expense=json.createObjectNode().put("amount",88.50).put("category","TRAVEL")
                    .put("expenseDate",java.time.LocalDate.now().minusDays(1).toString())
                    .put("invoiceNumber","SYNTHETIC-INVOICE").put("reason","controlled generic value");
            return freeze(code,routes,expense);
        }
        formKey="approval-it-"+UUID.randomUUID().toString().replace("-","");
        long form=jdbc.queryForObject("INSERT INTO approval_form(tenant_id,form_key,form_name,schema_json,status,created_by) VALUES (?,?,?,'{\"fields\":[{\"name\":\"reason\",\"label\":\"Reason\",\"type\":\"textarea\",\"required\":true}]}','ENABLED',?) RETURNING id",Long.class,tenant,formKey,formKey,owner.id());
        var nodes=json.createArrayNode();
        nodes.addObject().put("approveType","USER").put("targetKey",Long.toString(approver));
        long process=jdbc.queryForObject("INSERT INTO approval_process(tenant_id,process_key,process_name,form_id,node_json,status,created_by) VALUES (?,?,?,?,?,'ENABLED',?) RETURNING id",Long.class,tenant,formKey,formKey,form,nodes.toString(),owner.id());
        var args=json.createObjectNode(); version=0; application=0; initialStatus="DRAFT";
        if(code==ToolCode.APPROVAL_APPLICATION_CREATE_DRAFT || code==ToolCode.APPROVAL_APPLICATION_APPLY) args.put("formKey",formKey).put("processKey",formKey);
        else {
            application=jdbc.queryForObject("INSERT INTO approval_application(tenant_id,applicant_user_id,form_id,process_id,form_key,form_name,title,data_json,status) VALUES (?,?,?,?,?,?,?,'{\"reason\":\"existing fixture\"}','DRAFT') RETURNING id",Long.class,tenant,owner.id(),form,process,formKey,formKey,formKey);
            if(List.of(ToolCode.APPROVAL_APPLICATION_WITHDRAW,ToolCode.APPROVAL_APPLICATION_REMIND,ToolCode.APPROVAL_APPLICATION_REOPEN).contains(code)) {
                // Preparing history uses authorized human writes; restore the tool's least privilege before freezing/executing it.
                var setupPermissions=(code==ToolCode.APPROVAL_APPLICATION_REOPEN
                        ? List.of("approval:submit","approval:withdraw") : List.of("approval:submit"))
                        .stream().filter(permission->!permissions.contains(permission)).toList();
                for(String permission:setupPermissions)
                    jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,?)",tenant,owner.role(),permission);
                try {
                    var submitted=approvals.submitDraft(owner.id(),application,new VersionRequest(0));
                    version=submitted.version(); initialStatus="PENDING";
                    if(code==ToolCode.APPROVAL_APPLICATION_REOPEN) { version=approvals.withdraw(owner.id(),application,new VersionRequest(version)).version();initialStatus="WITHDRAWN"; }
                } finally {
                    for(String permission:setupPermissions)
                        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code=?",tenant,owner.role(),permission);
                }
                assertThat(access.resolveActiveUser(owner.id()).permissions()).containsExactlyInAnyOrderElementsOf(permissions);
            }
            args.put("applicationId",application).put("version",version);
        }
        if(code==ToolCode.APPROVAL_APPLICATION_CREATE_DRAFT || code==ToolCode.APPROVAL_APPLICATION_UPDATE_DRAFT || code==ToolCode.APPROVAL_APPLICATION_APPLY)
            args.putArray("fields").addObject().put("name","reason").put("value","controlled generic value");
        return freeze(code,routes,args);
    }
    private Task freeze(ToolCode code,List<String> routes,com.fasterxml.jackson.databind.JsonNode args) {
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code=?",code.code());
        jdbc.update("DELETE FROM agent_page_action_policy WHERE tenant_id=? AND page_id=? AND tool_code=?",tenant,routes.get(0),code.code());
        assertThat(jdbc.queryForObject("SELECT schema_hash FROM agent_tool WHERE tenant_id IS NULL AND code=?",String.class,code.code()))
                .as("persisted schema contract: %s",code.code()).isEqualTo(catalog.find(code.code()).orElseThrow().schemaHash());
        assertThat(registry.resolveAvailability(tenant,code.code()).status().name())
                .as("fixture runtime availability: %s; platform=%s; tenant=%s",code.code(),policies.platformTool(code.code()),policies.tenantTool(tenant,code.code())).isEqualTo("AVAILABLE");
        assertThat(pages.allowsTool(routes.get(0),code.code(),access.resolveActiveUser(owner.id()).permissions())).as("fixture entry permissions: %s",code.code()).isTrue();
        return new FrozenWriteTaskFixture(jdbc,json,hashing,catalog).create(tenant,owner.id(),routes.get(0),code.code(),args);
    }
    private void installTransactionProbe() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS approval_write_probe(application_id bigint,kind text,transaction_id bigint)");
        jdbc.execute("TRUNCATE approval_write_probe");
        jdbc.execute("CREATE OR REPLACE FUNCTION approval_write_probe_fn() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF TG_TABLE_NAME='approval_application' THEN INSERT INTO approval_write_probe VALUES (NEW.id,'BUSINESS',txid_current()); ELSIF NEW.resource_type='GENERIC_APPROVAL' AND NEW.resource_id ~ '^[0-9]+$' THEN INSERT INTO approval_write_probe VALUES (NEW.resource_id::bigint,'AUDIT',txid_current()); END IF; RETURN NEW; END $$");
        jdbc.execute("DROP TRIGGER IF EXISTS approval_write_business_probe ON approval_application");
        jdbc.execute("CREATE TRIGGER approval_write_business_probe AFTER INSERT OR UPDATE ON approval_application FOR EACH ROW EXECUTE FUNCTION approval_write_probe_fn()");
        jdbc.execute("DROP TRIGGER IF EXISTS approval_write_audit_probe ON business_audit_log");
        jdbc.execute("CREATE TRIGGER approval_write_audit_probe AFTER INSERT ON business_audit_log FOR EACH ROW EXECUTE FUNCTION approval_write_probe_fn()");
    }
    private String confirm(Task task) throws Exception { return confirm(task,owner); }
    private String confirm(Task task,Actor actor) throws Exception {
        var response=response("POST",path(task)+"/confirmation-token",actor,json.createObjectNode().put("planVersion",1).put("planHash",task.planHash()).toString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).path("data").path("confirmationToken").asText();
    }
    private HttpResponse<String> execute(Task task,String token) throws Exception { return execute(task,token,owner); }
    private HttpResponse<String> execute(Task task,String token,Actor actor) throws Exception {
        var body=json.createObjectNode().put("planVersion",1).put("planHash",task.planHash());
        if(token!=null)body.put("confirmationToken",token);
        return response("POST",path(task)+"/execute",actor,body.toString());
    }
    private HttpResponse<String> response(String method,String path,Actor actor,String body) throws Exception {
        return AgentHttpTestFixture.client().send(http.request(method,path,actor,"POST".equals(method)&&path.endsWith("/execute")?UUID.randomUUID().toString():null,body).build(),HttpResponse.BodyHandlers.ofString());
    }
    private String path(Task task) { return "/api/ai/tasks/"+task.number(); }
    private void terminal(Task task,String expected) {
        await().atMost(Duration.ofSeconds(25)).until(()->{worker.poll();return !List.of("QUEUED","RUNNING").contains(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?",String.class,task.id()));});
        assertThat(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?",String.class,task.id()))
                .as("terminal error=%s",jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo(expected);
    }
    private void unchanged(Task task) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked",Long.class,task.id())).isZero();
        if(application==0) assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_application WHERE applicant_user_id=?",Long.class,owner.id())).isZero();
        else assertThat(jdbc.queryForObject("SELECT version FROM approval_application WHERE id=?",Integer.class,application)).isEqualTo(version);
    }
}
