package com.aiworkmate.agent.task;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.AiTaskPlanRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentClarificationServiceTest {
    private final AgentTaskMapper tasks = mock(AgentTaskMapper.class);
    private final AgentTaskEventService events = mock(AgentTaskEventService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final AgentClarificationService service = new AgentClarificationService(tasks, events, json);
    private final AuthenticatedUser user = new AuthenticatedUser(7L, "owned", 1L, "EMPLOYEE",
            List.of("EMPLOYEE"), List.of("todo:read"), List.of("SELF"), 1L);
    private final AgentTask source = new AgentTask();
    private final AiTaskPlanRequest request = new AiTaskPlanRequest();
    @BeforeEach void setup() {
        source.setId(8L); source.setTenantId(1L); source.setUserId(7L); source.setVersion(2L);
        source.setTaskNo(UUID.randomUUID().toString()); source.setPageId("todo-list"); source.setStatus("NEEDS_INPUT");
        source.setInput("驳回当前申请"); source.setPlan("{\"clarificationDepth\":0}");
        source.setPageContext("{\"applicationId\":25}"); source.setTraceId("owned-trace");
        source.setConfirmationExpiresAt(LocalDateTime.now().plusMinutes(10));
        request.setInput("理由是资料不完整"); request.setPageId("todo-list"); request.setClarificationTaskId(source.getTaskNo());
        when(tasks.selectOwnedForUpdate(1L, 7L, source.getTaskNo())).thenReturn(source);
    }
    @Test void noReferenceDoesNotAccessOrCancelAnyTask() {
        request.setClarificationTaskId(null);
        var continuation = service.load(request, user); service.consume(continuation);
        assertThat(continuation.input()).isEqualTo(request.getInput()); assertThat(continuation.source()).isNull();
        verifyNoInteractions(tasks, events);
    }
    @Test void onlyTheBoundedStoredQuestionIsPassedBackToThePlanner() {
        source.setPlan("{\"clarificationDepth\":0,\"summary\":\"请提供任务编号\"}");
        var continuation = service.load(request, user);
        assertThat(service.question(continuation)).isEqualTo("请提供任务编号");
        for (String summary : List.of("null", "42", "\"\"", "\"" + "x".repeat(501) + "\"")) {
            source.setPlan("{\"clarificationDepth\":0,\"summary\":" + summary + "}");
            assertThatThrownBy(() -> service.question(continuation)).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(events);
    }
    @Test void continuationKeepsContextAndClosesOnlyTheLockedSourceAtItsVersion() throws Exception {
        var continuation = service.load(request, user);
        assertThat(continuation.input()).isEqualTo(source.getInput() + "\n\n" + request.getInput());
        assertThat(continuation.depth()).isOne();
        assertThat(service.context(continuation, json.readTree("{\"applicationId\":999}"))).isEqualTo(json.readTree(source.getPageContext()));
        when(tasks.cancelOwned(8L, 1L, 7L, "NEEDS_INPUT", 2L)).thenReturn(1, 0);
        service.consume(continuation);
        assertThatThrownBy(() -> service.consume(continuation)).isInstanceOf(BusinessException.class);
        verify(events).publish(eq(8L), eq("snapshot"), argThat(node -> "CANCELLED".equals(node.path("status").asText())), eq("owned-trace"));
    }
    @Test void unownedCrossPageAndCrossConversationReferencesAreRejected() {
        when(tasks.selectOwnedForUpdate(1L, 7L, source.getTaskNo())).thenReturn(null);
        assertThatThrownBy(() -> service.load(request, user)).isInstanceOf(BusinessException.class);
        when(tasks.selectOwnedForUpdate(1L, 7L, source.getTaskNo())).thenReturn(source);
        request.setPageId("visitor-booking");
        assertThatThrownBy(() -> service.load(request, user)).isInstanceOf(BusinessException.class);
        request.setPageId(source.getPageId()); request.setConversationId(20L);
        assertThatThrownBy(() -> service.load(request, user)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(events);
    }
    @Test void invalidReferencesCorruptDepthAndUtf8OverflowsNeverConsumeTheSource() {
        request.setClarificationTaskId("1-2-3-4-5");
        assertThatThrownBy(() -> service.load(request, user)).isInstanceOf(BusinessException.class);
        request.setClarificationTaskId(source.getTaskNo());
        for (String plan : List.of("null", "{}", "{\"clarificationDepth\":\"0\"}", "{\"clarificationDepth\":3}")) {
            source.setPlan(plan);
            assertThatThrownBy(() -> service.load(request, user)).isInstanceOf(BusinessException.class);
        }
        source.setPlan("{\"clarificationDepth\":0}"); request.setInput("界".repeat(1366));
        assertThatThrownBy(() -> service.load(request, user)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(events);
    }
    @Test void deletionOfAConversationCannotTurnItsQuestionIntoAnUnboundRequest() {
        source.setConversationId(20L); request.setConversationId(20L);
        source.setPlan("{\"clarificationDepth\":0,\"clarificationConversationId\":20}");
        assertThat(service.load(request, user).source()).isSameAs(source);
        source.setConversationId(null); request.setConversationId(null);
        assertThatThrownBy(() -> service.load(request, user)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(events);
    }
    @Test void expiredCancelledAndNonQuestionStatesCannotBeConsumed() {
        for (String status : List.of("CANCELLED", "EXPIRED", "PLAN_READY", "SUCCEEDED")) {
            source.setStatus(status);
            assertThatThrownBy(() -> service.consume(service.load(request, user))).isInstanceOf(BusinessException.class);
        }
        source.setStatus("NEEDS_INPUT"); source.setConfirmationExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThatThrownBy(() -> service.consume(service.load(request, user))).isInstanceOf(BusinessException.class);
        source.setConfirmationExpiresAt(null);
        assertThatThrownBy(() -> service.consume(service.load(request, user))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(events);
    }
    @Test void lastRoundMayCompleteButNotAskAgainAndQuestionsCannotExecute() {
        var continuation = new AgentClarificationService.Continuation(source, "complete", 3);
        var plan = json.createObjectNode(); service.decorate(plan, continuation, false);
        assertThat(plan.path("clarificationDepth").asInt()).isEqualTo(3);
        assertThatThrownBy(() -> service.decorate(json.createObjectNode(), continuation, true)).isInstanceOf(BusinessException.class);
        var machine = new AgentTaskStateMachine();
        assertThat(machine.canTransition(AgentTaskStatus.PLANNING, AgentTaskStatus.NEEDS_INPUT)).isTrue();
        assertThat(machine.cancellable(AgentTaskStatus.NEEDS_INPUT)).isTrue();
        assertThat(machine.canTransition(AgentTaskStatus.NEEDS_INPUT, AgentTaskStatus.EXPIRED)).isTrue();
        assertThat(machine.canTransition(AgentTaskStatus.NEEDS_INPUT, AgentTaskStatus.QUEUED)).isFalse();
        assertThat(machine.canTransition(AgentTaskStatus.NEEDS_INPUT, AgentTaskStatus.RUNNING)).isFalse();
    }
}
