package com.aiworkmate.agent.gateway;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.registry.ConfirmationPolicy;
import com.aiworkmate.agent.registry.OwnershipPolicy;
import com.aiworkmate.agent.registry.PermissionMode;
import com.aiworkmate.agent.registry.RetryPolicy;
import com.aiworkmate.agent.registry.RiskLevel;
import com.aiworkmate.agent.registry.SideEffect;
import com.aiworkmate.agent.registry.ToolDefinition;
import com.aiworkmate.agent.registry.ToolRegistry;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.tool.internal.ToolHandler;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultToolGatewayPolicyTest {
    private static final String LEASE_TOKEN = "0123456789abcdef0123456789abcdef";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentHashing hashing = new AgentHashing(objectMapper);
    private final GatewayExecutionSnapshotMapper snapshotMapper = mock(GatewayExecutionSnapshotMapper.class);
    private final ToolRegistry toolRegistry = mock(ToolRegistry.class);
    private final UserAccessService userAccessService = mock(UserAccessService.class);
    private final GatewayAuditWriter auditWriter = mock(GatewayAuditWriter.class);
    private final ToolHandler handler = mock(ToolHandler.class);
    private final AgentRuntimeProperties properties = new AgentRuntimeProperties();

    private ToolDefinition definition;
    private GatewayExecutionSnapshot snapshot;
    private DefaultToolGateway gateway;

    @BeforeEach
    void setUp() throws Exception {
        reset(snapshotMapper, toolRegistry, userAccessService, auditWriter, handler);
        properties.setEnabled(true);
        properties.setExecutionEnabled(true);
        definition = definition();
        snapshot = snapshot();

        when(handler.toolCode()).thenReturn("todo.query");
        when(handler.handlerVersion()).thenReturn("1.0.0");
        when(snapshotMapper.selectSnapshot(10L)).thenReturn(snapshot);
        when(snapshotMapper.reserveToolCall(anyLong(), anyLong(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(1);
        when(toolRegistry.resolveExecutableTool(1L, "todo.query")).thenReturn(Optional.of(definition));
        when(userAccessService.resolveActiveUser(7L)).thenReturn(new ResolvedUserAccess(
                7L, "user", 1L, "EMPLOYEE", List.of("EMPLOYEE"), List.of("todo:read"), List.of("SELF"), 1L
        ));
        when(auditWriter.record(any(), any(), anyString(), anyBoolean())).thenReturn("decision-1");
        when(handler.execute(any(), any())).thenReturn(objectMapper.readTree("{\"items\":[]}"));

        gateway = new DefaultToolGateway(
                properties, snapshotMapper, toolRegistry, userAccessService, hashing,
                new ToolSchemaValidator(), new ToolOutputGuard(), new HandlerResolver(List.of(handler)),
                auditWriter, objectMapper
        );
    }

    @Test
    void shouldExecuteOnlyAfterAllChecksAndPreAuditPass() {
        ToolGatewayResult result = execute();

        assertThat(result.decision()).isEqualTo(GatewayDecision.ALLOW);
        assertThat(result.code()).isEqualTo(GatewayDecisionCode.ALLOWED);
        assertThat(result.output().path("items").isArray()).isTrue();
        verify(auditWriter).record(snapshot, GatewayDecision.ALLOW, "POLICY_ALLOWED", false);
        verify(handler).execute(any(), any());
        verify(auditWriter).complete(eq("decision-1"), eq(true), eq("SUCCEEDED"), eq(12), isNull(), anyLong());
    }

    @Test
    void forgedLeaseMustBeStaleAndNeverReachHandler() {
        ToolGatewayResult result = gateway.execute(
                10L, new WorkerLease("forged-worker", 0, LEASE_TOKEN)
        );

        assertThat(result.decision()).isEqualTo(GatewayDecision.STALE);
        verify(handler, never()).execute(any(), any());
    }

    @Test
    void tamperedArgumentsMustBeStaleAndNeverReachHandler() {
        snapshot.setArguments("{\"limit\":11}");

        assertThat(execute().decision()).isEqualTo(GatewayDecision.STALE);
        verify(handler, never()).execute(any(), any());
    }

    @Test
    void stepNotBoundToPersistedPlanMustBeStaleAndNeverReachHandler() throws Exception {
        JsonNode alteredPlan = objectMapper.readTree(snapshot.getPlan());
        ((com.fasterxml.jackson.databind.node.ObjectNode) alteredPlan.path("steps").get(0))
                .put("toolVersion", "forged-version");
        snapshot.setPlan(alteredPlan.toString());
        snapshot.setPlanHash(hashing.hash(alteredPlan));

        assertThat(execute().decision()).isEqualTo(GatewayDecision.STALE);
        verify(handler, never()).execute(any(), any());
    }

    @Test
    void revokedPermissionMustDenyAndNeverReachHandler() {
        when(userAccessService.resolveActiveUser(7L)).thenReturn(new ResolvedUserAccess(
                7L, "user", 1L, "EMPLOYEE", List.of("EMPLOYEE"), List.of(), List.of("SELF"), 2L
        ));

        assertThat(execute().decision()).isEqualTo(GatewayDecision.DENY);
        verify(handler, never()).execute(any(), any());
    }

    @Test
    void registryFailureMustFailClosedBeforeHandler() {
        when(toolRegistry.resolveExecutableTool(1L, "todo.query")).thenThrow(new IllegalStateException("registry down"));

        assertThat(execute().decision()).isEqualTo(GatewayDecision.UNAVAILABLE);
        verify(handler, never()).execute(any(), any());
    }

    @Test
    void malformedSchemaArgumentsMustDenyBeforeHandler() throws Exception {
        snapshot.setArguments("{\"limit\":10,\"userId\":99}");
        snapshot.setArgsHash(hashing.hash(objectMapper.readTree(snapshot.getArguments())));
        JsonNode reboundPlan = objectMapper.readTree(snapshot.getPlan());
        ((com.fasterxml.jackson.databind.node.ObjectNode) reboundPlan.path("steps").get(0))
                .put("argsHash", snapshot.getArgsHash());
        snapshot.setPlan(reboundPlan.toString());
        snapshot.setPlanHash(hashing.hash(reboundPlan));

        assertThat(execute().decision()).isEqualTo(GatewayDecision.DENY);
        verify(handler, never()).execute(any(), any());
    }

    @Test
    void preAuditFailureMustFailClosedBeforeHandler() {
        doThrow(new IllegalStateException("audit down"))
                .when(auditWriter).record(any(), any(), anyString(), anyBoolean());

        assertThat(execute().decision()).isEqualTo(GatewayDecision.UNAVAILABLE);
        verify(handler, never()).execute(any(), any());
    }

    @Test
    void atomicBudgetReservationFailureMustThrottleBeforeHandler() {
        when(snapshotMapper.reserveToolCall(anyLong(), anyLong(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(0);

        assertThat(execute().decision()).isEqualTo(GatewayDecision.THROTTLED);
        verify(handler, never()).execute(any(), any());
    }

    @Test
    void expiredStepDeadlineMustBeStaleBeforeHandler() {
        snapshot.setStepTimeoutAt(LocalDateTime.now().minusSeconds(1));

        assertThat(execute().decision()).isEqualTo(GatewayDecision.STALE);
        verify(handler, never()).execute(any(), any());
    }

    @Test
    void dangerousHandlerOutputMustNotEscapeGateway() throws Exception {
        when(handler.execute(any(), any())).thenReturn(objectMapper.readTree(
                "{\"items\":[],\"token\":\"should-not-leak\"}"
        ));

        ToolGatewayResult result = execute();

        assertThat(result.code()).isEqualTo(GatewayDecisionCode.TOOL_RESULT_INVALID);
        assertThat(result.output()).isNull();
        verify(handler).execute(any(), any());
    }

    private ToolGatewayResult execute() {
        return gateway.execute(10L, new WorkerLease("worker-1", 0, LEASE_TOKEN));
    }

    private ToolDefinition definition() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {"type":"object","additionalProperties":false,"properties":{"limit":{"type":"integer","minimum":1,"maximum":50}},"required":["limit"]}
                """);
        JsonNode output = objectMapper.readTree("""
                {"type":"object","additionalProperties":false,"properties":{"items":{"type":"array","maxItems":50}},"required":["items"]}
                """);
        return ToolDefinition.create(
                "todo.query", "Todo query", "Read own todos", "Own todo lookup", "1.0.0",
                input, output, RiskLevel.L0, Set.of("todo:read"), PermissionMode.ALL,
                OwnershipPolicy.SELF, RetryPolicy.READ_ONLY_SAFE, SideEffect.NONE,
                ConfirmationPolicy.NONE, 50, 262144, 15000, "FULL"
        );
    }

    private GatewayExecutionSnapshot snapshot() throws Exception {
        String arguments = "{\"limit\":10}";
        String argsHash = hashing.hash(objectMapper.readTree(arguments));
        var planNode = objectMapper.createObjectNode();
        planNode.put("planVersion", 1);
        var plannedStep = planNode.putArray("steps").addObject();
        plannedStep.put("sequence", 1);
        plannedStep.put("toolCode", "todo.query");
        plannedStep.put("toolVersion", "1.0.0");
        plannedStep.put("schemaHash", definition.schemaHash());
        plannedStep.put("argsHash", argsHash);
        plannedStep.put("riskLevel", "L0");
        plannedStep.put("sideEffect", "NONE");
        plannedStep.put("confirmationPolicy", "NONE");
        String plan = planNode.toString();
        GatewayExecutionSnapshot value = new GatewayExecutionSnapshot();
        value.setTaskId(5L);
        value.setStepId(10L);
        value.setSequenceNo(1);
        value.setTenantId(1L);
        value.setUserId(7L);
        value.setTaskStatus("RUNNING");
        value.setStepStatus("RUNNING");
        value.setWorkerId("worker-1");
        value.setLeaseTokenHash(hashing.sha256(LEASE_TOKEN));
        value.setLeaseUntil(LocalDateTime.now().plusMinutes(1));
        value.setTaskTimeoutAt(LocalDateTime.now().plusMinutes(1));
        value.setStepTimeoutAt(LocalDateTime.now().plusSeconds(15));
        value.setTaskAttempt(0);
        value.setStepAttempt(0);
        value.setPlan(plan);
        value.setPlanHash(hashing.hash(objectMapper.readTree(plan)));
        value.setPlanVersion(1);
        value.setTaskRiskLevel("L0");
        value.setToolCallCount(0);
        value.setToolCode("todo.query");
        value.setToolVersion("1.0.0");
        value.setSchemaHash(definition.schemaHash());
        value.setArguments(arguments);
        value.setArgsHash(argsHash);
        value.setStepRiskLevel("L0");
        value.setTraceId("trace-1");
        return value;
    }
}
