package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.ConversationResponse;
import com.aiworkmate.dto.MessageResponse;
import com.aiworkmate.entity.Conversation;
import com.aiworkmate.entity.Message;
import com.aiworkmate.mapper.ConversationMapper;
import com.aiworkmate.mapper.MessageMapper;
import com.aiworkmate.service.ConversationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(Long userId) {
        return conversationMapper.selectList(
                        new LambdaQueryWrapper<Conversation>()
                                .eq(Conversation::getUserId, userId)
                                .orderByDesc(Conversation::getUpdatedAt)
                )
                .stream()
                .map(this::toConversationResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> listMessages(Long userId, Long conversationId) {
        ensureConversationOwner(userId, conversationId);
        return messageMapper.selectList(
                        new LambdaQueryWrapper<Message>()
                                .eq(Message::getConversationId, conversationId)
                                .orderByAsc(Message::getCreatedAt)
                )
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    private void ensureConversationOwner(Long userId, Long conversationId) {
        Long count = conversationMapper.selectCount(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getId, conversationId)
                        .eq(Conversation::getUserId, userId)
        );
        if (count == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权访问该对话");
        }
    }

    private ConversationResponse toConversationResponse(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getModel(),
                conversation.getUpdatedAt()
        );
    }

    private MessageResponse toMessageResponse(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
