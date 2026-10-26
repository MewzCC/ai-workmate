package com.aiworkmate.integration;

import com.aiworkmate.agent.capability.PageCapabilityCatalog;
import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.aiworkmate.integration.AgentHttpTestFixture.Actor;
import com.aiworkmate.integration.FrozenWriteTaskFixture.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Synthetic single-write acceptance only in the owner fixture's disposable database and Redis. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="AGENT_TEST_DB_URL",matches="jdbc:postgresql:.*")
class AgentLeaveWriteIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT=new AgentIntegrationEnvironment();
    private static final String JWT_SECRET=UUID.randomUUID().toString()+UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment(){return ENVIRONMENT;}
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private PasswordEncoder passwords;
    @Autowired private ToolCatalog catalog;
    @Autowired private PageCapabilityCatalog pages;
    @Autowired private AgentHashing hashing;
    @Autowired private AgentTaskWorker worker;
    @Autowired private AgentRuntimeProperties runtime;
    private AgentHttpTestFixture http;
    private long tenant;
    private Actor owner;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        ENVIRONMENT.register(p);
        p.add("server.address",()->"127.0.0.1");
        p.add("jwt.secret",()->JWT_SECRET);
        p.add("app.auth.cookie-name",()->"oa_write_it");
        p.add("app.auth.cookie-secure",()->"false");
        p.add("spring.ai.openai.api-key",()->"development-only-unconfigured");
        p.add("agent.enabled",()->"true");
        p.add("agent.execution-enabled",()->"true");
        p.add("agent.write-tools-enabled",()->"true"); // This context is verified against the owned disposable DB.
        p.add("agent.worker.poll-delay-ms",()->"3600000");
        p.add("agent.worker.heartbeat-delay-ms",()->"3600000");
    }
    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo(ENVIRONMENT.schema());
        tenant=jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'",Long.class);
        http=new AgentHttpTestFixture(jdbc,json,passwords,port,"en-US");
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=true WHERE tenant_id=?",tenant);
        runtime.setWriteToolsEnabled(true);
    }
    @AfterEach void finish() {
        if(owner!=null) {
            jdbc.update("UPDATE agent_task SET status='CANCELLED' WHERE user_id=? AND status IN ('WAITING_CONFIRMATION','PLAN_READY','QUEUED')",owner.id());
            await().atMost(Duration.ofSeconds(20)).untilAsserted(()->assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM agent_task WHERE user_id=? AND status='RUNNING'",Long.class,owner.id())).isZero());
        }
        jdbc.execute("DROP TRIGGER IF EXISTS agent_leave_audit_fault ON business_audit_log");
        jdbc.execute("DROP FUNCTION IF EXISTS agent_leave_audit_fault()");
        jdbc.execute("DROP TRIGGER IF EXISTS agent_leave_invocation_fault ON agent_tool_invocation");
        jdbc.execute("DROP FUNCTION IF EXISTS agent_leave_invocation_fault()");
    }

    @ParameterizedTest(name="confirmed leave write {0}")
    @EnumSource(value=ToolCode.class,names={"LEAVE_APPLY","LEAVE_CREATE_DRAFT","LEAVE_UPDATE_DRAFT","LEAVE_SUBMIT","LEAVE_WITHDRAW","LEAVE_REMIND"})
    void confirmedSingleWriteReturnsTheActualBusinessResult(ToolCode code) throws Exception {
        var task=plan(code);
        installTransactionProbe();
        String token=confirmation(task,owner);
        String storedHash=jdbc.queryForObject("SELECT confirmation_token_hash FROM agent_task WHERE id=?",String.class,task.id());
        assertThat(storedHash.equals(hashing.sha256(token))).isTrue();
        assertThat(storedHash.equals(token)).isFalse();
        assertThat(execute(task,owner,token,UUID.randomUUID().toString()).statusCode()).isEqualTo(202);
        assertTerminal(task,"SUCCEEDED");
        var detail=response("GET",path(task),owner,null,null);
        assertThat(detail.statusCode()).isEqualTo(200);
        var step=json.readTree(detail.body()).path("data").path("steps").get(0);
        var result=step.path("result");
        long application=result.path("applicationId").asLong();
        assertThat(application).isPositive();
        String expected=switch(code) {
            case LEAVE_CREATE_DRAFT,LEAVE_UPDATE_DRAFT -> "DRAFT";
            case LEAVE_WITHDRAW -> "WITHDRAWN";
            default -> "PENDING";
        };
        assertThat(result.path("status").asText()).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT status FROM leave_application WHERE tenant_id=? AND applicant_user_id=? AND id=?",String.class,tenant,owner.id(),application)).isEqualTo(expected);
        if(List.of(ToolCode.LEAVE_CREATE_DRAFT,ToolCode.LEAVE_UPDATE_DRAFT,ToolCode.LEAVE_APPLY).contains(code)) {
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM leave_application WHERE id=? AND tenant_id=? AND applicant_user_id=?
                    AND leave_type='PERSONAL' AND start_date=?::date AND end_date=?::date
                    AND start_period='AM' AND end_period='PM' AND reason='isolated confirmed leave' AND duration_half_days=2
                    """,Long.class,application,tenant,owner.id(),LocalDate.now().plusDays(3).toString(),LocalDate.now().plusDays(3).toString())).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked AND outcome='SUCCEEDED'",Long.class,task.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE tenant_id=? AND actor_user_id=? AND resource_type='LEAVE_APPLICATION' AND resource_id=? AND result='SUCCESS'",Long.class,tenant,owner.id(),Long.toString(application))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT transaction_id) FROM leave_write_tx_probe WHERE application_id=?",Long.class,application))
                .as("business mutation and business audit must commit in the same transaction").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT kind) FROM leave_write_tx_probe WHERE application_id=?",Long.class,application)).isEqualTo(2);
        assertThat(step.path("resultSummary").asText()).isNotBlank();
        if(code==ToolCode.LEAVE_APPLY || code==ToolCode.LEAVE_SUBMIT) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE tenant_id=? AND business_id=? AND business_type='LEAVE_APPLICATION' AND status='PENDING'",Long.class,tenant,application)).isEqualTo(1);
        }
        if(code==ToolCode.LEAVE_WITHDRAW) {
            assertThat(jdbc.queryForObject("SELECT status FROM workflow_instance WHERE tenant_id=? AND business_id=? AND business_type='LEAVE_APPLICATION'",String.class,tenant,application)).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE tenant_id=? AND business_id=? AND business_type='LEAVE_APPLICATION' AND status='CANCELLED'",Long.class,tenant,application)).isEqualTo(1);
        }
        if(code==ToolCode.LEAVE_REMIND) {
            assertThat(jdbc.queryForObject("SELECT reminder_count FROM workflow_task WHERE tenant_id=? AND business_id=? AND business_type='LEAVE_APPLICATION' AND last_reminded_at IS NOT NULL",Integer.class,tenant,application)).isEqualTo(1);
        }
    }

    @Test void aMissingConfirmationNeverEnqueuesOrWrites() throws Exception {
        var task=plan(ToolCode.LEAVE_APPLY);
        var denied=execute(task,owner,null,UUID.randomUUID().toString());
        assertThat(denied.statusCode()).isEqualTo(409);
        assertThat(json.readTree(denied.body()).path("errorCode").asText()).isEqualTo("CONFIRMATION_REQUIRED");
        assertNoInvocationOrApplication(task);
    }
    @Test void revokedToolPermissionRejectsConfirmationAndExecution() throws Exception {
        var task=plan(ToolCode.LEAVE_APPLY);
        String token=confirmation(task,owner);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='agent:tool:leave.apply'",tenant,owner.role());
        assertThat(response("POST",path(task)+"/confirmation-token",owner,null,confirmationBody(task)).statusCode()).isEqualTo(403);
        assertThat(execute(task,owner,token,UUID.randomUUID().toString()).statusCode()).isEqualTo(403);
        assertNoInvocationOrApplication(task);
    }
    @Test void anotherUserOrTenantCannotConfirmOrExecuteTheFrozenPlan() throws Exception {
        var task=plan(ToolCode.LEAVE_APPLY);
        String token=confirmation(task,owner);
        long otherTenant=jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'isolated write tenant') RETURNING id",Long.class,"WRITE_"+UUID.randomUUID().toString().replace("-",""));
        for(var stranger:List.of(http.createAndLogin(tenant,List.of()),http.createAndLogin(otherTenant,List.of()))) {
            assertThat(response("POST",path(task)+"/confirmation-token",stranger,null,confirmationBody(task)).statusCode()).isEqualTo(404);
            assertThat(execute(task,stranger,token,UUID.randomUUID().toString()).statusCode()).isEqualTo(404);
        }
        assertNoInvocationOrApplication(task);
    }

    @Test void concurrentExecutionAndTokenReplayCannotCreateASecondApplication() throws Exception {
        var task=plan(ToolCode.LEAVE_APPLY);
        String token=confirmation(task,owner), key=UUID.randomUUID().toString();
        var calls=java.util.stream.IntStream.range(0,2).mapToObj(i->java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try{return execute(task,owner,token,key);}
            catch(Exception failure){throw new java.util.concurrent.CompletionException(failure);}
        })).toList();
        for(var call:calls)assertThat(call.get(15,java.util.concurrent.TimeUnit.SECONDS).statusCode()).isEqualTo(202);
        assertTerminal(task,"SUCCEEDED");
        assertThat(execute(task,owner,token,UUID.randomUUID().toString()).statusCode()).isEqualTo(409);
        assertThat(execute(task,owner,"wrong-token",key).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_application WHERE applicant_user_id=?",Long.class,owner.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_instance WHERE applicant_id=? AND business_type='LEAVE_APPLICATION'",Long.class,owner.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked",Long.class,task.id())).isEqualTo(1);
    }

    @Test void stalePlanWrongAndExpiredConfirmationTokensNeverWrite() throws Exception {
        var task=plan(ToolCode.LEAVE_APPLY);
        String token=confirmation(task,owner);
        var stale=json.createObjectNode().put("planVersion",1).put("planHash","sha256:"+"0".repeat(64));
        assertThat(response("POST",path(task)+"/confirmation-token",owner,null,stale.toString()).statusCode()).isEqualTo(409);
        assertThat(execute(task,owner,"wrong-token",UUID.randomUUID().toString()).statusCode()).isEqualTo(409);
        jdbc.update("UPDATE agent_task SET confirmation_expires_at=CURRENT_TIMESTAMP-INTERVAL '1 minute' WHERE id=?",task.id());
        assertThat(execute(task,owner,token,UUID.randomUUID().toString()).statusCode()).isEqualTo(409);
        assertNoInvocationOrApplication(task);
    }

    @Test void aValidTokenCannotBeUsedForAnotherPlan() throws Exception {
        var original=plan(ToolCode.LEAVE_APPLY);
        String token=confirmation(original,owner);
        var args=json.readTree(jdbc.queryForObject("SELECT args::text FROM agent_task_step WHERE task_id=?",String.class,original.id()));
        String page=jdbc.queryForObject("SELECT page_id FROM agent_task WHERE id=?",String.class,original.id());
        var other=new FrozenWriteTaskFixture(jdbc,json,hashing,catalog).create(tenant,owner.id(),page,ToolCode.LEAVE_APPLY.code(),args);
        assertThat(execute(other,owner,token,UUID.randomUUID().toString()).statusCode()).isEqualTo(409);
        assertNoInvocationOrApplication(original);
        assertNoInvocationOrApplication(other);
    }

    @Test void closingTheWriteSwitchAfterConfirmationRejectsExecution() throws Exception {
        var task=plan(ToolCode.LEAVE_APPLY);
        String token=confirmation(task,owner);
        runtime.setWriteToolsEnabled(false);
        assertThat(execute(task,owner,token,UUID.randomUUID().toString()).statusCode()).isEqualTo(403);
        assertNoInvocationOrApplication(task);
    }

    @Test void aStaleBusinessVersionDoesNotChangeTheDraft() throws Exception {
        var task=plan(ToolCode.LEAVE_UPDATE_DRAFT,args->args.put("version",7));
        assertThat(execute(task,owner,confirmation(task,owner),UUID.randomUUID().toString()).statusCode()).isEqualTo(202);
        assertTerminal(task,"FAILED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo("TOOL_STATE_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT version FROM leave_application WHERE applicant_user_id=?",Integer.class,owner.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT reason FROM leave_application WHERE applicant_user_id=?",String.class,owner.id())).isEqualTo("existing fixture");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE actor_user_id=? AND result='SUCCESS'",Long.class,owner.id())).isZero();
    }

    @Test void aSecondConfirmedReminderIsRateLimitedWithoutAnotherBusinessChange() throws Exception {
        var first=plan(ToolCode.LEAVE_REMIND);
        assertThat(execute(first,owner,confirmation(first,owner),UUID.randomUUID().toString()).statusCode()).isEqualTo(202);
        assertTerminal(first,"SUCCEEDED");
        long application=jdbc.queryForObject("SELECT id FROM leave_application WHERE applicant_user_id=?",Long.class,owner.id());
        String page=jdbc.queryForObject("SELECT page_id FROM agent_task WHERE id=?",String.class,first.id());
        var args=json.createObjectNode().put("applicationId",application).put("version",1);
        var second=new FrozenWriteTaskFixture(jdbc,json,hashing,catalog).create(tenant,owner.id(),page,ToolCode.LEAVE_REMIND.code(),args);
        assertThat(execute(second,owner,confirmation(second,owner),UUID.randomUUID().toString()).statusCode()).isEqualTo(202);
        assertTerminal(second,"FAILED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,second.id())).isEqualTo("GATEWAY_THROTTLED");
        assertThat(jdbc.queryForObject("SELECT version FROM leave_application WHERE id=?",Integer.class,application)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reminder_count FROM workflow_task WHERE tenant_id=? AND business_type='LEAVE_APPLICATION' AND business_id=?",Integer.class,tenant,application)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE actor_user_id=? AND action='REMIND' AND result='SUCCESS'",Long.class,owner.id())).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void anotherApplicantsDraftIsRejectedByTheDomain(boolean anotherTenant) throws Exception {
        var task=plan(ToolCode.LEAVE_UPDATE_DRAFT,args->{
            long resourceTenant=anotherTenant ? jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'foreign resource fixture') RETURNING id",Long.class,"RES_"+UUID.randomUUID().toString().replace("-","")) : tenant;
            long foreign=jdbc.queryForObject("INSERT INTO app_user(tenant_id,username,password) VALUES (?,?,'synthetic fixture') RETURNING id",Long.class,resourceTenant,"foreign-"+UUID.randomUUID());
            long draft=jdbc.queryForObject("""
                    INSERT INTO leave_application(tenant_id,applicant_user_id,leave_type,start_date,start_period,end_date,end_period,duration_half_days,reason,status)
                    VALUES (?,?,'PERSONAL',CURRENT_DATE+3,'AM',CURRENT_DATE+3,'PM',2,'foreign fixture','DRAFT') RETURNING id
                    """,Long.class,resourceTenant,foreign);
            args.put("applicationId",draft);
        });
        assertThat(execute(task,owner,confirmation(task,owner),UUID.randomUUID().toString()).statusCode()).isEqualTo(202);
        assertTerminal(task,"FAILED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo("TOOL_RESOURCE_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_application WHERE reason='foreign fixture' AND version<>0",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_application WHERE applicant_user_id=? AND version<>0",Long.class,owner.id())).isZero();
    }

    @Test void revokingTheSourceRouteInvalidatesAnAlreadyConfirmedPlan() throws Exception {
        var task=plan(ToolCode.LEAVE_APPLY);
        String token=confirmation(task,owner);
        String page=jdbc.queryForObject("SELECT page_id FROM agent_task WHERE id=?",String.class,task.id());
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code=?",tenant,owner.role(),"route:"+page);
        assertThat(execute(task,owner,token,UUID.randomUUID().toString()).statusCode()).isEqualTo(403);
        assertNoInvocationOrApplication(task);
    }

    @Test void missingApproverRollsBackTheIntermediateDraft() throws Exception {
        var task=plan(ToolCode.LEAVE_APPLY,args->{
            args.remove("approverUserId");
            jdbc.update("UPDATE app_user SET approver_user_id=NULL WHERE tenant_id=? AND id=?",tenant,owner.id());
        });
        assertThat(execute(task,owner,confirmation(task,owner),UUID.randomUUID().toString()).statusCode()).isEqualTo(202);
        assertTerminal(task,"FAILED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo("TOOL_STATE_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_application WHERE applicant_user_id=?",Long.class,owner.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_instance WHERE applicant_id=?",Long.class,owner.id())).isZero();
    }

    @Test void aFailedGatewayPreAuditNeverCallsTheBusinessHandler() throws Exception {
        var task=plan(ToolCode.LEAVE_APPLY);
        jdbc.execute("CREATE FUNCTION agent_leave_invocation_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.task_id="+task.id()+" THEN RAISE EXCEPTION 'synthetic gateway pre-audit fault'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER agent_leave_invocation_fault BEFORE INSERT ON agent_tool_invocation FOR EACH ROW EXECUTE FUNCTION agent_leave_invocation_fault()");
        assertThat(execute(task,owner,confirmation(task,owner),UUID.randomUUID().toString()).statusCode()).isEqualTo(202);
        assertTerminal(task,"FAILED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo("GATEWAY_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=?",Long.class,task.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_application WHERE applicant_user_id=?",Long.class,owner.id())).isZero();
    }

    @Test void businessAuditFailureRollsBackTheWholeAtomicApplicationAndIsNeverRetried() throws Exception {
        var task=plan(ToolCode.LEAVE_APPLY);
        jdbc.execute("CREATE FUNCTION agent_leave_audit_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.actor_user_id="+owner.id()+" THEN RAISE EXCEPTION 'synthetic business audit fault'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER agent_leave_audit_fault BEFORE INSERT ON business_audit_log FOR EACH ROW EXECUTE FUNCTION agent_leave_audit_fault()");
        assertThat(execute(task,owner,confirmation(task,owner),UUID.randomUUID().toString()).statusCode()).isEqualTo(202);
        assertTerminal(task,"PARTIALLY_SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo("TOOL_RESULT_UNKNOWN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_application WHERE applicant_user_id=?",Long.class,owner.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_instance WHERE applicant_id=?",Long.class,owner.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE actor_user_id=?",Long.class,owner.id())).isZero();
        worker.poll();worker.heartbeatAndRecover();worker.poll();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked",Long.class,task.id())).isEqualTo(1);
    }

    @Test void postCommitInvocationAuditFailureReportsUnknownWithoutRepeatingTheBusinessWrite() throws Exception {
        var task=plan(ToolCode.LEAVE_APPLY);
        String token=confirmation(task,owner), key=UUID.randomUUID().toString();
        jdbc.execute("CREATE FUNCTION agent_leave_invocation_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.task_id="+task.id()+" AND NEW.outcome='SUCCEEDED' THEN RAISE EXCEPTION 'synthetic invocation audit fault'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER agent_leave_invocation_fault BEFORE UPDATE ON agent_tool_invocation FOR EACH ROW EXECUTE FUNCTION agent_leave_invocation_fault()");
        assertThat(execute(task,owner,token,key).statusCode()).isEqualTo(202);
        assertTerminal(task,"PARTIALLY_SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo("TOOL_RESULT_UNKNOWN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_application WHERE applicant_user_id=? AND status='PENDING'",Long.class,owner.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE actor_user_id=? AND action='AGENT_APPLY' AND result='SUCCESS'",Long.class,owner.id())).isEqualTo(1);
        assertThat(execute(task,owner,token,key).statusCode()).isEqualTo(202);
        worker.poll();worker.heartbeatAndRecover();worker.poll();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_application WHERE applicant_user_id=?",Long.class,owner.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT tool_call_count FROM agent_task WHERE id=?",Integer.class,task.id())).isEqualTo(1);
    }

    private Task plan(ToolCode code) throws Exception {return plan(code,args->{});}
    private Task plan(ToolCode code,java.util.function.Consumer<ObjectNode> amend) throws Exception {
        var definition=catalog.find(code.code()).orElseThrow();
        var permissions=new LinkedHashSet<>(definition.requiredPermissions());
        var routes=pages.businessPageIdsForTool(code.code()).stream().sorted().toList();
        routes.forEach(route->permissions.add("route:"+route));
        permissions.add("agent:tool:"+code.code());
        owner=http.createAndLogin(tenant,List.copyOf(permissions));
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code=?",code.code());
        jdbc.update("DELETE FROM agent_page_action_policy WHERE tenant_id=? AND page_id=? AND tool_code=?",tenant,routes.get(0),code.code());
        ObjectNode args=json.createObjectNode();
        if(List.of(ToolCode.LEAVE_APPLY,ToolCode.LEAVE_CREATE_DRAFT,ToolCode.LEAVE_UPDATE_DRAFT).contains(code)) {
            args.put("leaveType","PERSONAL").put("startDate",LocalDate.now().plusDays(3).toString()).put("startPeriod","AM")
                    .put("endDate",LocalDate.now().plusDays(3).toString()).put("endPeriod","PM").put("reason","isolated confirmed leave");
        }
        if(code==ToolCode.LEAVE_APPLY || code==ToolCode.LEAVE_CREATE_DRAFT) args.put("approverUserId",approver());
        if(code==ToolCode.LEAVE_UPDATE_DRAFT || code==ToolCode.LEAVE_SUBMIT) {
            long draft=jdbc.queryForObject("""
                    INSERT INTO leave_application(tenant_id,applicant_user_id,approver_user_id,leave_type,start_date,start_period,end_date,end_period,duration_half_days,reason,status)
                    VALUES (?,?,?,'PERSONAL',CURRENT_DATE+3,'AM',CURRENT_DATE+3,'PM',2,'existing fixture','DRAFT') RETURNING id
                    """,Long.class,tenant,owner.id(),approver());
            args.put("applicationId",draft).put("version",0);
        }
        if(code==ToolCode.LEAVE_WITHDRAW || code==ToolCode.LEAVE_REMIND) {
            long pending=LeaveReadFixture.pending(jdbc,tenant,owner.id(),approver());
            jdbc.update("UPDATE leave_application SET workflow_instance_id=(SELECT id FROM workflow_instance WHERE tenant_id=? AND business_type='LEAVE_APPLICATION' AND business_id=?) WHERE tenant_id=? AND id=?",tenant,pending,tenant,pending);
            args.put("applicationId",pending).put("version",0);
        }
        amend.accept(args);
        return new FrozenWriteTaskFixture(jdbc,json,hashing,catalog).create(tenant,owner.id(),routes.get(0),code.code(),args);
    }
    private long approver() {
        return LeaveWriteFixture.linkedApprover(jdbc,tenant,owner.id());
    }
    private void installTransactionProbe() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS leave_write_tx_probe(application_id bigint,kind text,transaction_id bigint)");
        jdbc.execute("TRUNCATE leave_write_tx_probe");
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION leave_write_tx_probe_function() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF TG_TABLE_NAME='leave_application' THEN
                    INSERT INTO leave_write_tx_probe VALUES (NEW.id,'BUSINESS',txid_current());
                  ELSIF NEW.resource_type='LEAVE_APPLICATION' AND NEW.resource_id ~ '^[0-9]+$' THEN
                    INSERT INTO leave_write_tx_probe VALUES (NEW.resource_id::bigint,'AUDIT',txid_current());
                  END IF;
                  RETURN NEW;
                END $$;
                """);
        jdbc.execute("DROP TRIGGER IF EXISTS leave_write_business_probe ON leave_application");
        jdbc.execute("CREATE TRIGGER leave_write_business_probe AFTER INSERT OR UPDATE ON leave_application FOR EACH ROW EXECUTE FUNCTION leave_write_tx_probe_function()");
        jdbc.execute("DROP TRIGGER IF EXISTS leave_write_audit_probe ON business_audit_log");
        jdbc.execute("CREATE TRIGGER leave_write_audit_probe AFTER INSERT ON business_audit_log FOR EACH ROW EXECUTE FUNCTION leave_write_tx_probe_function()");
    }
    private String confirmation(Task task,Actor actor) throws Exception {
        var response=response("POST",path(task)+"/confirmation-token",actor,null,confirmationBody(task));
        assertThat(response.statusCode()).as("confirmation HTTP status").isEqualTo(200);
        return json.readTree(response.body()).path("data").path("confirmationToken").asText();
    }
    private String confirmationBody(Task task){return json.createObjectNode().put("planVersion",1).put("planHash",task.planHash()).toString();}
    private HttpResponse<String> execute(Task task,Actor actor,String token,String key) throws Exception {
        var body=json.createObjectNode().put("planVersion",1).put("planHash",task.planHash());
        if(token!=null)body.put("confirmationToken",token);
        return response("POST",path(task)+"/execute",actor,key,body.toString());
    }
    private HttpResponse<String> response(String method,String path,Actor actor,String key,String body) throws Exception {
        return AgentHttpTestFixture.client().send(http.request(method,path,actor,key,body).build(),HttpResponse.BodyHandlers.ofString());
    }
    private void assertTerminal(Task task,String expected) {
        await().atMost(Duration.ofSeconds(25)).until(()->{
            worker.poll();
            return !List.of("QUEUED","RUNNING").contains(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?",String.class,task.id()));
        });
        assertThat(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?",String.class,task.id()))
                .as("terminal error=%s",jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo(expected);
    }
    private void assertNoInvocationOrApplication(Task task) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked",Long.class,task.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_application WHERE applicant_user_id=?",Long.class,owner.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo("WAITING_CONFIRMATION");
    }
    private String path(Task task){return "/api/ai/tasks/"+task.number();}
}
