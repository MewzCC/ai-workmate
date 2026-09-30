package com.aiworkmate.service.impl;

import com.aiworkmate.agent.task.AgentReadResultPresenter;
import com.aiworkmate.agent.task.AgentTask;
import com.aiworkmate.agent.task.AgentTaskMapper;
import com.aiworkmate.agent.task.AgentTaskStep;
import com.aiworkmate.agent.task.AgentTaskStepMapper;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.entity.Conversation;
import com.aiworkmate.entity.Message;
import com.aiworkmate.mapper.ConversationMapper;
import com.aiworkmate.mapper.MessageMapper;
import com.aiworkmate.security.AuthenticatedUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Persists a verified L0 task answer in the owner's chat; never executes a tool. */
@Service
@RequiredArgsConstructor
public class AgentChatResultService {
    private final AgentTaskMapper taskMapper;
    private final AgentTaskStepMapper stepMapper;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public String appendTodoResult(AuthenticatedUser user, Long conversationId, String taskNo) {
        if (!user.permissions().contains("route:ai-workspace")
                || !user.permissions().contains("todo:read")
                || !user.permissions().contains("agent:tool:todo.query")) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        Conversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getId, conversationId)
                .eq(Conversation::getTenantId, user.tenantId())
                .eq(Conversation::getUserId, user.userId()));
        if (conversation == null) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);

        AgentTask task = taskMapper.selectOwnedForUpdate(user.tenantId(), user.userId(), taskNo);
        if (task == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!"ai-workspace".equals(task.getPageId()) || !"SUCCEEDED".equals(task.getStatus())
                || !"L0".equals(task.getMaxRiskLevel())) throw new BusinessException(ErrorCode.INVALID_TASK_STATE);

        List<AgentTaskStep> steps = stepMapper.selectByTaskId(task.getId());
        if (steps.size() != 1 || !"todo.query".equals(steps.get(0).getToolCode())
                || !"SUCCEEDED".equals(steps.get(0).getStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        JsonNode arguments;
        if (steps.get(0).getArgs() == null) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        try {
            arguments = objectMapper.readTree(steps.get(0).getArgs());
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        if (arguments == null || !arguments.isObject()
                || (arguments.hasNonNull("status") && !"PENDING".equals(arguments.path("status").asText()))
                || arguments.hasNonNull("from") || arguments.hasNonNull("to")
                || (arguments.hasNonNull("page") && (!arguments.path("page").isIntegralNumber()
                || arguments.path("page").asInt() != 1))) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        Message existing = messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                .eq(Message::getSourceTaskNo, taskNo));
        if (existing != null) {
            if (!conversationId.equals(existing.getConversationId())) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
            return existing.getContent();
        }
        if (steps.get(0).getResult() == null) throw new BusinessException(ErrorCode.REQUEST_INVALID);

        JsonNode result;
        try {
            result = objectMapper.readTree(steps.get(0).getResult());
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        String answer = AgentReadResultPresenter.todo(result);
        LocalDateTime now = LocalDateTime.now();
        Message prompt = new Message();
        prompt.setConversationId(conversationId);
        prompt.setRole("user");
        prompt.setContent(task.getInput());
        prompt.setStatus("success");
        prompt.setTokenCount(0);
        prompt.setCreatedAt(now);
        messageMapper.insert(prompt);

        Message reply = new Message();
        reply.setConversationId(conversationId);
        reply.setRole("assistant");
        reply.setContent(answer);
        reply.setStatus("success");
        reply.setSourceTaskNo(taskNo);
        reply.setTokenCount(0);
        reply.setCreatedAt(now.plusNanos(1_000));
        messageMapper.insert(reply);
        if (conversation.getTitle() == null || "新对话".equals(conversation.getTitle())) {
            conversation.setTitle(task.getInput().length() > 40 ? task.getInput().substring(0, 40) : task.getInput());
        }
        conversation.setUpdatedAt(now);
        conversationMapper.updateById(conversation);
        return answer;
    }
}
