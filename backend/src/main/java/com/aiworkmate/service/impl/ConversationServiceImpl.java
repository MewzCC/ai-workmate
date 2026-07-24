package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.AttachmentResponse;
import com.aiworkmate.dto.ConversationResponse;
import com.aiworkmate.dto.CreateConversationRequest;
import com.aiworkmate.dto.MessageResponse;
import com.aiworkmate.entity.Conversation;
import com.aiworkmate.entity.Message;
import com.aiworkmate.mapper.ConversationMapper;
import com.aiworkmate.mapper.MessageMapper;
import com.aiworkmate.service.AttachmentService;
import com.aiworkmate.service.ConversationService;
import com.aiworkmate.service.model.AiModelCatalog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final String DEFAULT_TITLE = "新对话";

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final AttachmentService attachmentService;

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(Long userId, String search) {
        List<Conversation> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, userId)
                        .orderByDesc(Conversation::getUpdatedAt));
        if (search == null || search.isBlank()) return conversations.stream().map(this::toResponse).toList();
        if (conversations.isEmpty()) return List.of();

        String keyword = search.strip().toLowerCase();
        List<Long> messageMatches = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                        .in(Message::getConversationId, conversations.stream().map(Conversation::getId).toList())
                        .like(Message::getContent, search.strip()))
                .stream().map(Message::getConversationId).distinct().toList();
        return conversations.stream()
                .filter(item -> item.getTitle().toLowerCase().contains(keyword) || messageMatches.contains(item.getId()))
                .map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public ConversationResponse createConversation(Long userId, CreateConversationRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(normalizeTitle(request.title()));
        conversation.setModel(normalizeModel(request.model()));
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);
        return toResponse(conversation);
    }

    @Override
    @Transactional
    public ConversationResponse renameConversation(Long userId, Long conversationId, String title) {
        Conversation conversation = requireOwned(userId, conversationId);
        conversation.setTitle(title.strip());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
        return toResponse(conversation);
    }

    @Override
    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        requireOwned(userId, conversationId);
        attachmentService.deleteConversationAttachments(userId, conversationId);
        messageMapper.delete(new LambdaQueryWrapper<Message>().eq(Message::getConversationId, conversationId));
        conversationMapper.deleteById(conversationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> listMessages(Long userId, Long conversationId) {
        requireOwned(userId, conversationId);
        List<Message> messages = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId).orderByAsc(Message::getCreatedAt));
        Map<Long, List<AttachmentResponse>> attachments = attachmentService
                .listByMessageIds(messages.stream().map(Message::getId).toList())
                .stream().collect(Collectors.groupingBy(AttachmentResponse::messageId));
        return messages.stream().map(message -> toMessageResponse(message, attachments.getOrDefault(message.getId(), List.of()))).toList();
    }

    @Override
    @Transactional
    public void updateFeedback(Long userId, Long messageId, String feedback) {
        Message message = messageMapper.selectById(messageId);
        if (message == null || !"assistant".equals(message.getRole())) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "只能评价 AI 回复");
        }
        requireOwned(userId, message.getConversationId());
        message.setFeedback("none".equals(feedback) ? null : feedback);
        messageMapper.updateById(message);
    }

    private Conversation requireOwned(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getId, conversationId).eq(Conversation::getUserId, userId));
        if (conversation == null) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN, "无权访问该会话");
        return conversation;
    }

    private String normalizeTitle(String title) {
        return title == null || title.isBlank() ? DEFAULT_TITLE : title.strip();
    }

    private String normalizeModel(String model) {
        return AiModelCatalog.normalize(model);
    }

    private ConversationResponse toResponse(Conversation conversation) {
        return new ConversationResponse(conversation.getId(), conversation.getTitle(), conversation.getModel(),
                conversation.getCreatedAt(), conversation.getUpdatedAt());
    }

    private MessageResponse toMessageResponse(Message message, List<AttachmentResponse> attachments) {
        return new MessageResponse(message.getId(), message.getRole(), message.getContent(), message.getStatus(),
                message.getFeedback(), attachments, message.getCreatedAt());
    }
}
