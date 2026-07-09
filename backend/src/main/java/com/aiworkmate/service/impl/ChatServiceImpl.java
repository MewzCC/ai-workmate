package com.aiworkmate.service.impl;

import com.aiworkmate.entity.Conversation;
import com.aiworkmate.entity.Message;
import com.aiworkmate.mapper.ConversationMapper;
import com.aiworkmate.mapper.MessageMapper;
import com.aiworkmate.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

/**
 * AI 对话服务 — 核心业务层
 *
 * 第 1 月实现：单轮/多轮对话 + 流式输出
 * 第 3 月升级：Tool Calling + Agent + 对话记忆持久化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    // 第 1 月用内存存储对话历史，第 2 月升级为 Redis + PostgreSQL
    private final MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder().build();

    @Override
    public Flux<String> chatStream(Long userId, Long conversationId,
                                   String userMessage, String model) {
        // 1. 确保对话存在
        Conversation conv = getOrCreateConversation(userId, conversationId, model);

        // 2. 保存用户消息
        saveMessage(conv.getId(), "user", userMessage);

        // 3. 构建 ChatClient 请求
        var advisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(String.valueOf(conv.getId()))
                .build();

        // 4. 流式返回
        return chatClient.prompt()
                .user(userMessage)
                .advisors(advisor)
                .stream()
                .content()
                .doOnComplete(() -> log.info("Chat stream completed for conv={}", conv.getId()))
                .doOnError(e -> log.error("Chat stream error", e));
    }

    @Override
    public String chat(Long userId, Long conversationId, String userMessage, String model) {
        Conversation conv = getOrCreateConversation(userId, conversationId, model);
        saveMessage(conv.getId(), "user", userMessage);

        String response = chatClient.prompt()
                .user(userMessage)
                .call()
                .content();

        saveMessage(conv.getId(), "assistant", response);
        return response;
    }

    // ===== 私有方法 =====

    private Conversation getOrCreateConversation(Long userId, Long conversationId, String model) {
        if (conversationId != null) {
            Conversation conv = conversationMapper.selectById(conversationId);
            if (conv != null) return conv;
        }

        Conversation conv = new Conversation();
        conv.setUserId(userId);
        conv.setTitle("新对话");
        conv.setModel(model != null ? model : "deepseek-chat");
        conv.setCreatedAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(conv);
        return conv;
    }

    private void saveMessage(Long conversationId, String role, String content) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
    }
}
