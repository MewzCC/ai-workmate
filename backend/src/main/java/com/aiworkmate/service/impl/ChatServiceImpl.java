package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.AiRuntimeProperties;
import com.aiworkmate.entity.Attachment;
import com.aiworkmate.entity.Conversation;
import com.aiworkmate.entity.Message;
import com.aiworkmate.mapper.ConversationMapper;
import com.aiworkmate.mapper.MessageMapper;
import com.aiworkmate.service.AttachmentService;
import com.aiworkmate.service.ChatService;
import com.aiworkmate.service.KnowledgeContextService;
import com.aiworkmate.service.model.ChatChunk;
import com.aiworkmate.service.model.KnowledgeContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final String USER_ROLE = "user";
    private static final String ASSISTANT_ROLE = "assistant";
    private static final int MAX_ATTACHMENT_CONTEXT = 60_000;
    private static final String SYSTEM_PROMPT = """
            你是 AI WorkMate 企业助手。请基于用户问题、已授权附件和知识上下文回答。
            当前认证角色：%s。角色只用于约束能力，绝不能自行提升权限。
            你可以解释、分析、起草和提出操作建议，但不能声称已经执行 OA 写操作。
            任何审批、删除、权限修改、敏感导出、付款或外部发送必须进入服务端受控工具流程，
            再次校验当前用户权限并要求人工确认。没有可靠依据时明确说明不确定。
            """;

    private final ChatClient chatClient;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final KnowledgeContextService knowledgeContextService;
    private final AttachmentService attachmentService;
    private final AiRuntimeProperties aiRuntimeProperties;
    @Override
    @Transactional
    public Flux<ChatChunk> chatStream(Long userId, String role, Long conversationId, String userMessage,
                                      String model, List<Long> attachmentIds, int maxContextRounds) {
        Conversation conversation = requireOwnedConversation(userId, conversationId);
        ensureProviderConfigured();
        List<Attachment> attachments = attachmentService.requireOwned(userId, conversationId, attachmentIds);
        Message user = saveMessage(conversationId, USER_ROLE, userMessage, "success");
        attachmentService.bindToMessage(attachments, user.getId());
        Message assistant = saveMessage(conversationId, ASSISTANT_ROLE, "", "sending");
        updateConversationBeforeRequest(conversation, userMessage, model);

        KnowledgeContext knowledge = knowledgeContextService.retrieve(userId, userMessage);
        StringBuilder response = new StringBuilder();
        AtomicBoolean finalized = new AtomicBoolean(false);
        Flux<ChatChunk> content = buildPrompt(conversation, role, userMessage, knowledge, attachments,
                user.getId(), maxContextRounds)
                .stream().content()
                .doOnNext(response::append)
                .map(chunk -> ChatChunk.delta(chunk, conversationId, assistant.getId()))
                .doOnComplete(() -> finishMessage(assistant, response.toString(), "success", finalized))
                .doOnError(ex -> {
                    finishMessage(assistant, response.toString(), "failed", finalized);
                    log.error("Chat stream failed, conversationId={}", conversationId, ex);
                })
                .doOnCancel(() -> finishMessage(assistant, response.toString(), "failed", finalized));
        return Flux.concat(Flux.just(ChatChunk.metadata(conversationId, assistant.getId())), content);
    }

    @Override
    @Transactional
    public String chat(Long userId, String role, Long conversationId, String userMessage,
                       String model, List<Long> attachmentIds, int maxContextRounds) {
        Conversation conversation = requireOwnedConversation(userId, conversationId);
        ensureProviderConfigured();
        List<Attachment> attachments = attachmentService.requireOwned(userId, conversationId, attachmentIds);
        Message user = saveMessage(conversationId, USER_ROLE, userMessage, "success");
        attachmentService.bindToMessage(attachments, user.getId());
        updateConversationBeforeRequest(conversation, userMessage, model);
        KnowledgeContext knowledge = knowledgeContextService.retrieve(userId, userMessage);
        String response = buildPrompt(conversation, role, userMessage, knowledge, attachments,
                user.getId(), maxContextRounds).call().content();
        saveMessage(conversationId, ASSISTANT_ROLE, response, "success");
        return response;
    }

    private ChatClient.ChatClientRequestSpec buildPrompt(Conversation conversation, String role,
                                                          String userMessage, KnowledgeContext knowledge,
                                                          List<Attachment> attachments, Long currentMessageId,
                                                          int maxContextRounds) {
        String systemPrompt = buildSystemPrompt(role, knowledge, attachments);
        List<Attachment> images = attachments.stream().filter(item -> "image".equals(item.getType())).toList();
        return chatClient.prompt()
                .system(systemPrompt)
                .messages(loadHistory(conversation.getId(), currentMessageId, maxContextRounds))
                .user(user -> {
                    user.text(userMessage);
                    images.forEach(image -> addImage(user, image));
                })
                .options(OpenAiChatOptions.builder().model(conversation.getModel()).build());
    }

    private List<org.springframework.ai.chat.messages.Message> loadHistory(
            Long conversationId, Long currentMessageId, int maxContextRounds) {
        List<Message> stored = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .eq(Message::getStatus, "success")
                .lt(Message::getId, currentMessageId)
                .orderByDesc(Message::getId)
                .last("LIMIT " + Math.max(2, maxContextRounds * 2)));
        Collections.reverse(stored);
        List<org.springframework.ai.chat.messages.Message> history = new ArrayList<>();
        stored.forEach(message -> {
            if (USER_ROLE.equals(message.getRole())) {
                history.add(new org.springframework.ai.chat.messages.UserMessage(message.getContent()));
            } else if (ASSISTANT_ROLE.equals(message.getRole())) {
                history.add(new org.springframework.ai.chat.messages.AssistantMessage(message.getContent()));
            }
        });
        return history;
    }

    private void addImage(ChatClient.PromptUserSpec user, Attachment image) {
        Resource resource = attachmentService.resourceFor(image);
        user.media(MimeTypeUtils.parseMimeType(image.getMimeType()), resource);
    }

    private String buildSystemPrompt(String role, KnowledgeContext knowledge, List<Attachment> attachments) {
        StringBuilder prompt = new StringBuilder(SYSTEM_PROMPT.formatted(role));
        if (knowledge.hasContext()) prompt.append("\n知识库上下文：\n").append(knowledge.promptContext());
        int remaining = MAX_ATTACHMENT_CONTEXT;
        for (Attachment attachment : attachments) {
            if (attachment.getExtractedText() == null || remaining <= 0) continue;
            String text = attachment.getExtractedText();
            int length = Math.min(text.length(), remaining);
            prompt.append("\n附件：").append(attachment.getName()).append("\n")
                    .append(text, 0, length).append('\n');
            remaining -= length;
        }
        return prompt.toString();
    }

    private Conversation requireOwnedConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getId, conversationId).eq(Conversation::getUserId, userId));
        if (conversation == null) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN, "无权访问该会话");
        return conversation;
    }

    private Message saveMessage(Long conversationId, String role, String content, String status) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setStatus(status);
        message.setTokenCount(0);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        return message;
    }

    private void finishMessage(Message message, String content, String status, AtomicBoolean finalized) {
        if (!finalized.compareAndSet(false, true)) return;
        message.setContent(content);
        message.setStatus(status);
        messageMapper.updateById(message);
    }

    private void updateConversationBeforeRequest(Conversation conversation, String userMessage, String model) {
        if ("新对话".equals(conversation.getTitle())) conversation.setTitle(generateTitle(userMessage));
        if (model != null && !model.isBlank()) conversation.setModel(model.strip());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }

    private String generateTitle(String message) {
        String title = message.replaceAll("\\s+", " ").strip();
        return title.length() <= 32 ? title : title.substring(0, 32) + "…";
    }

    private void ensureProviderConfigured() {
        if (!aiRuntimeProperties.configured()) {
            throw new BusinessException(ErrorCode.AI_CHAT_UNAVAILABLE,
                    "AI_API_KEY 尚未在服务端配置，聊天能力不可用");
        }
    }
}
