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
import com.aiworkmate.dto.PageCapabilityResponse;
import com.aiworkmate.mapper.ConversationMapper;
import com.aiworkmate.mapper.MessageMapper;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.PageCapabilityService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Persists a verified gateway task answer in the owner's chat; never executes a tool. */
@Service
@RequiredArgsConstructor
public class AgentChatResultService {
    private static final java.util.Set<String> TERMINAL = java.util.Set.of("SUCCEEDED", "PARTIALLY_SUCCEEDED", "FAILED", "TIMED_OUT", "REJECTED", "EXPIRED", "CANCELLED");
    private final AgentTaskMapper taskMapper;
    private final AgentTaskStepMapper stepMapper;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;
    private final PageCapabilityService pageCapabilityService;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Conversation lockPlanningConversation(AuthenticatedUser user, String pageId, Long conversationId) {
        if (conversationId == null) return null;
        if (!"ai-workspace".equals(pageId) || !user.permissions().contains("route:ai-workspace"))
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        Conversation conversation = conversationMapper.lockOwned(user.tenantId(), user.userId(), conversationId);
        if (conversation == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return conversation;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void persistPlanPrompt(Conversation conversation, AgentTask task) {
        Message prompt = new Message();
        prompt.setConversationId(conversation.getId());
        prompt.setRole("user"); prompt.setContent(task.getInput()); prompt.setStatus("success");
        prompt.setTokenCount(0); prompt.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(prompt);
        updateConversation(conversation, task);
    }

    @Transactional(readOnly = true)
    public AgentTask latestTask(AuthenticatedUser user, Long conversationId) {
        if (!user.permissions().contains("route:ai-workspace")) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        Conversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getId, conversationId).eq(Conversation::getTenantId, user.tenantId())
                .eq(Conversation::getUserId, user.userId()));
        if (conversation == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return taskMapper.latestForConversation(user.tenantId(), user.userId(), conversationId);
    }

    @Transactional
    public String appendResult(AuthenticatedUser user, Long conversationId, String taskNo) {
        if (!user.permissions().contains("route:ai-workspace")) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        Conversation conversation = conversationMapper.lockOwned(user.tenantId(), user.userId(), conversationId);
        if (conversation == null) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);

        AgentTask task = taskMapper.selectOwnedForUpdate(user.tenantId(), user.userId(), taskNo);
        if (task == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!"ai-workspace".equals(task.getPageId()) || !TERMINAL.contains(task.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_TASK_STATE);
        }
        if (task.getConversationId() != null && !conversationId.equals(task.getConversationId()))
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);

        List<AgentTaskStep> steps = stepMapper.selectByTaskId(task.getId());
        boolean succeeded = "SUCCEEDED".equals(task.getStatus());
        if (succeeded && (steps.isEmpty() || steps.size() > 3 || steps.stream().anyMatch(step -> !"SUCCEEDED".equals(step.getStatus())))) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        if (succeeded) {
            PageCapabilityResponse capability = pageCapabilityService.resolve(user.userId(), "ai-workspace");
            Map<String, PageCapabilityResponse.Tool> allowed = capability.tools().stream()
                    .collect(Collectors.toUnmodifiableMap(PageCapabilityResponse.Tool::code, Function.identity()));
            if (steps.stream().anyMatch(step -> !allowed.containsKey(step.getToolCode())))
                throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        Message existing = messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                .eq(Message::getSourceTaskNo, taskNo));
        if (existing != null) {
            if (!conversationId.equals(existing.getConversationId())) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
            return existing.getContent();
        }
        StringBuilder answer = new StringBuilder();
        if (!succeeded) answer.append(com.aiworkmate.common.MessageUtils.resolve("agent.chat.terminal."+task.getStatus(), taskNo));
        for (AgentTaskStep step : succeeded ? steps : List.<AgentTaskStep>of()) {
            if (step.getResult() == null) throw new BusinessException(ErrorCode.REQUEST_INVALID);
            JsonNode result;
            try {
                result = objectMapper.readTree(step.getResult());
            } catch (JsonProcessingException exception) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID);
            }
            if (!answer.isEmpty()) answer.append("\n\n");
            answer.append(AgentReadResultPresenter.present(step.getToolCode(), result));
        }
        LocalDateTime now = LocalDateTime.now();
        if (task.getConversationId() == null) {
            Message prompt = new Message();
            prompt.setConversationId(conversationId);
            prompt.setRole("user"); prompt.setContent(task.getInput()); prompt.setStatus("success");
            prompt.setTokenCount(0); prompt.setCreatedAt(now);
            messageMapper.insert(prompt);
        }

        Message reply = new Message();
        reply.setConversationId(conversationId);
        reply.setRole("assistant");
        reply.setContent(answer.toString());
        reply.setStatus(succeeded || "CANCELLED".equals(task.getStatus()) ? "success" : "failed");
        reply.setSourceTaskNo(taskNo);
        reply.setSourceToolCode(succeeded ? steps.get(0).getToolCode() : null);
        reply.setTokenCount(0);
        reply.setCreatedAt(now.plusNanos(1_000));
        messageMapper.insert(reply);
        updateConversation(conversation, task);
        return answer.toString();
    }

    private void updateConversation(Conversation conversation, AgentTask task) {
        if (conversation.getTitle() == null || "新对话".equals(conversation.getTitle())) {
            conversation.setTitle(task.getInput().length() > 40 ? task.getInput().substring(0, 40) : task.getInput());
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }
}
