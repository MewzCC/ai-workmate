package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.entity.Conversation;
import com.aiworkmate.entity.Message;
import com.aiworkmate.mapper.ConversationMapper;
import com.aiworkmate.mapper.MessageMapper;
import com.aiworkmate.service.ChatService;
import com.aiworkmate.service.KnowledgeContextService;
import com.aiworkmate.service.model.KnowledgeContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final String DEFAULT_MODEL = "deepseek-chat";
    private static final String USER_ROLE = "user";
    private static final String ASSISTANT_ROLE = "assistant";
    private static final String SYSTEM_PROMPT = """
            你是 AI WorkMate 企业助手。你需要基于用户问题和可用知识库上下文回答。
            只能回答当前用户有权访问的信息；没有可靠来源时要明确说明不确定。
            不得绕过鉴权、不得执行真实业务写操作、不得泄露密钥或敏感配置。
            """;

    private final ChatClient chatClient;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final KnowledgeContextService knowledgeContextService;

    private final MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder().build();

    @Override
    @Transactional
    public Flux<String> chatStream(Long userId, Long conversationId, String userMessage, String model) {
        Conversation conversation = getOrCreateConversation(userId, conversationId, model);
        saveMessage(conversation.getId(), USER_ROLE, userMessage);

        KnowledgeContext knowledgeContext = knowledgeContextService.retrieve(userId, userMessage);
        StringBuilder assistantMessage = new StringBuilder();

        return buildPrompt(conversation, userMessage, knowledgeContext)
                .stream()
                .content()
                .doOnNext(assistantMessage::append)
                .doOnError(ex -> log.error("Chat stream failed, conversationId={}", conversation.getId(), ex))
                .doOnComplete(() -> {
                    if (!assistantMessage.isEmpty()) {
                        saveMessage(conversation.getId(), ASSISTANT_ROLE, assistantMessage.toString());
                        touchConversation(conversation);
                    }
                })
                .doFinally(signal -> log.info(
                        "Chat stream finished, conversationId={}, signal={}", conversation.getId(), signal));
    }

    @Override
    @Transactional
    public String chat(Long userId, Long conversationId, String userMessage, String model) {
        Conversation conversation = getOrCreateConversation(userId, conversationId, model);
        saveMessage(conversation.getId(), USER_ROLE, userMessage);

        KnowledgeContext knowledgeContext = knowledgeContextService.retrieve(userId, userMessage);
        String response = buildPrompt(conversation, userMessage, knowledgeContext)
                .call()
                .content();

        saveMessage(conversation.getId(), ASSISTANT_ROLE, response);
        touchConversation(conversation);
        log.info("Chat completed, conversationId={}", conversation.getId());
        return response;
    }

    private ChatClient.ChatClientRequestSpec buildPrompt(Conversation conversation,
                                                         String userMessage,
                                                         KnowledgeContext knowledgeContext) {
        var advisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(String.valueOf(conversation.getId()))
                .build();

        return chatClient.prompt()
                .system(buildSystemPrompt(knowledgeContext))
                .user(userMessage)
                .advisors(advisor);
    }

    private String buildSystemPrompt(KnowledgeContext knowledgeContext) {
        if (!knowledgeContext.hasContext()) {
            return SYSTEM_PROMPT;
        }
        return SYSTEM_PROMPT + "\n可用知识库上下文：\n" + knowledgeContext.promptContext();
    }

    private Conversation getOrCreateConversation(Long userId, Long conversationId, String model) {
        if (conversationId != null) {
            return findOwnedConversation(userId, conversationId);
        }

        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle("新对话");
        conversation.setModel(normalizeModel(model));
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(conversation);
        return conversation;
    }

    private Conversation findOwnedConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getId, conversationId)
                        .eq(Conversation::getUserId, userId)
        );
        if (conversation == null) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN, "无权访问该对话");
        }
        return conversation;
    }

    private String normalizeModel(String model) {
        return model == null || model.isBlank() ? DEFAULT_MODEL : model;
    }

    private void saveMessage(Long conversationId, String role, String content) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setTokenCount(0);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }

    private void touchConversation(Conversation conversation) {
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }
}
