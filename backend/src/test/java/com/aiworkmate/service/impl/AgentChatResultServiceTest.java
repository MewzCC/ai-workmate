package com.aiworkmate.service.impl;

import com.aiworkmate.agent.task.AgentTask;
import com.aiworkmate.agent.task.AgentTaskMapper;
import com.aiworkmate.agent.task.AgentTaskStep;
import com.aiworkmate.agent.task.AgentTaskStepMapper;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.PageCapabilityResponse;
import com.aiworkmate.entity.Conversation;
import com.aiworkmate.entity.Message;
import com.aiworkmate.mapper.ConversationMapper;
import com.aiworkmate.mapper.MessageMapper;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.PageCapabilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentChatResultServiceTest {
    private final AgentTaskMapper tasks = mock(AgentTaskMapper.class);
    private final AgentTaskStepMapper steps = mock(AgentTaskStepMapper.class);
    private final ConversationMapper conversations = mock(ConversationMapper.class);
    private final MessageMapper messages = mock(MessageMapper.class);
    private final PageCapabilityService capabilities = mock(PageCapabilityService.class);
    private final AgentChatResultService service = new AgentChatResultService(
            tasks, steps, conversations, messages, new ObjectMapper(), capabilities);
    private final AuthenticatedUser user = new AuthenticatedUser(7L, "alice", 9L, "EMPLOYEE",
            List.of("EMPLOYEE"), List.of("route:ai-workspace", "todo:read", "agent:tool:todo.query"), List.of("SELF"), 1L);

    @Test
    void rejectsRevokedPermissionBeforeReadingConversation() {
        AuthenticatedUser revoked = new AuthenticatedUser(7L, "alice", 9L, "EMPLOYEE",
                List.of("EMPLOYEE"), List.of("todo:read"), List.of("SELF"), 2L);
        assertThatThrownBy(() -> service.appendResult(revoked, 3L, "task"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(conversations, tasks, steps, messages);
    }

    @Test
    void rejectsCrossTenantConversationAndForeignTask() {
        assertThatThrownBy(() -> service.appendResult(user, 3L, "task"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(tasks, steps, messages);

        Conversation owned = new Conversation();
        owned.setId(3L);
        when(conversations.selectOne(any())).thenReturn(owned);
        assertThatThrownBy(() -> service.appendResult(user, 3L, "foreign"))
                .isInstanceOf(BusinessException.class);
        verify(tasks).selectOwnedForUpdate(9L, 7L, "foreign");
        verifyNoInteractions(steps, messages);
    }

    @Test
    void onlyAcceptsSucceededSingleReadStepAndIsIdempotent() {
        Conversation owned = new Conversation();
        owned.setId(3L);
        when(conversations.selectOne(any())).thenReturn(owned);
        AgentTask task = new AgentTask();
        task.setId(11L);
        task.setPageId("ai-workspace");
        task.setStatus("SUCCEEDED");
        task.setMaxRiskLevel("L0");
        when(tasks.selectOwnedForUpdate(9L, 7L, "task")).thenReturn(task);
        AgentTaskStep step = new AgentTaskStep();
        step.setToolCode("todo.query");
        step.setArgs("{}");
        step.setStatus("SUCCEEDED");
        step.setResult("{\"total\":0,\"items\":[]}");
        when(steps.selectByTaskId(11L)).thenReturn(List.of(step));
        allow("todo.query");
        Message existing = new Message();
        existing.setConversationId(3L);
        existing.setContent("existing answer");
        when(messages.selectOne(any())).thenReturn(existing);
        assertThat(service.appendResult(user, 3L, "task")).isEqualTo("existing answer");
        verify(messages, never()).insert(any(Message.class));

        step.setToolCode("leave.apply");
        assertThatThrownBy(() -> service.appendResult(user, 3L, "task"))
                .isInstanceOf(BusinessException.class);
        verify(messages, never()).insert(any(Message.class));
    }

    @Test
    void persistsVerifiedWriteAnswerOnceAndBindsTaskSource() {
        Conversation owned = new Conversation();
        owned.setId(3L);
        owned.setTitle("新对话");
        when(conversations.selectOne(any())).thenReturn(owned);
        AgentTask task = new AgentTask();
        task.setId(11L);
        task.setPageId("ai-workspace");
        task.setStatus("SUCCEEDED");
        task.setMaxRiskLevel("L2");
        task.setInput("预订会议室");
        when(tasks.selectOwnedForUpdate(9L, 7L, "task")).thenReturn(task);
        AgentTaskStep step = new AgentTaskStep();
        step.setToolCode("meeting.book");
        step.setArgs("{}");
        step.setStatus("SUCCEEDED");
        step.setResult("{\"bookingId\":88,\"status\":\"BOOKED\"}");
        when(steps.selectByTaskId(11L)).thenReturn(List.of(step));
        allow("meeting.book");

        assertThat(service.appendResult(user, 3L, "task")).isNotBlank();
        ArgumentCaptor<Message> captured = ArgumentCaptor.forClass(Message.class);
        verify(messages, times(2)).insert(captured.capture());
        assertThat(captured.getAllValues().get(0).getContent()).isEqualTo("预订会议室");
        assertThat(captured.getAllValues().get(1).getSourceTaskNo()).isEqualTo("task");
        assertThat(captured.getAllValues().get(1).getSourceToolCode()).isEqualTo("meeting.book");
        assertThat(captured.getAllValues().get(1).getContent()).contains("bookingId: 88", "status: BOOKED");
        assertThat(captured.getAllValues().get(1).getRole()).isEqualTo("assistant");
        verify(conversations).updateById(owned);
    }

    private void allow(String... toolCodes) {
        List<PageCapabilityResponse.Tool> tools = java.util.Arrays.stream(toolCodes)
                .map(code -> new PageCapabilityResponse.Tool(code, code, code, "L0", "NONE", "NONE", "SELF"))
                .toList();
        when(capabilities.resolve(7L, "ai-workspace")).thenReturn(new PageCapabilityResponse(
                "ai-workspace", "AI_WORKSPACE", 1, List.of(), "SELF", List.of("SELF"),
                new PageCapabilityResponse.ContextSchema(4096, 3, List.of()), tools, null));
    }
}
