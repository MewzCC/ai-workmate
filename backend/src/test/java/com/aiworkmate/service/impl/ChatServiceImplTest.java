package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.AiRuntimeProperties;
import com.aiworkmate.entity.Conversation;
import com.aiworkmate.mapper.ConversationMapper;
import com.aiworkmate.mapper.MessageMapper;
import com.aiworkmate.service.AttachmentService;
import com.aiworkmate.service.KnowledgeContextService;
import com.aiworkmate.service.model.KnowledgeContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock private ChatClient chatClient;
    @Mock private ConversationMapper conversationMapper;
    @Mock private MessageMapper messageMapper;
    @Mock private KnowledgeContextService knowledgeContextService;
    @Mock private AttachmentService attachmentService;
    @Mock private AiRuntimeProperties aiRuntimeProperties;

    @InjectMocks
    private ChatServiceImpl chatService;

    @Test
    void shouldRejectChatWhenProviderKeyIsNotConfigured() {
        Conversation conversation = new Conversation();
        conversation.setId(2001L);
        conversation.setUserId(1001L);
        when(conversationMapper.selectOne(any())).thenReturn(conversation);
        when(aiRuntimeProperties.configured()).thenReturn(false);

        assertThatThrownBy(() -> chatService.chat(
                1001L, "USER", 2001L, "你好", "deepseek-chat", null, List.of(), 10))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        org.assertj.core.api.Assertions.assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.AI_CHAT_UNAVAILABLE.getErrorCode()));
    }

    @Test
    void shouldRejectUnknownModelBeforePersistingMessages() {
        Conversation conversation = new Conversation();
        conversation.setId(2001L);
        conversation.setUserId(1001L);
        when(conversationMapper.selectOne(any())).thenReturn(conversation);
        when(aiRuntimeProperties.configured()).thenReturn(true);

        assertThatThrownBy(() -> chatService.chat(
                1001L, "USER", 2001L, "你好", "unknown-model", null, List.of(), 10))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不支持的 AI 模型");

        verifyNoInteractions(messageMapper, attachmentService, knowledgeContextService);
    }

    @Test
    void shouldInstructPreciseCitationRulesInSystemPrompt() throws Exception {
        KnowledgeContext knowledge = new KnowledgeContext(
                "[知识来源1：handbook.txt，分块 0]\nAnnual leave policy",
                List.of(new KnowledgeContext.Reference("10", "20", "handbook.txt", 0.91, "Annual leave policy")));

        var method = ChatServiceImpl.class.getDeclaredMethod("buildSystemPrompt",
                String.class, KnowledgeContext.class, List.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        String prompt = (String) method.invoke(chatService, "USER", knowledge, List.of());

        assertThat(prompt).contains("像学术论文引用一样精确标注来源");
        assertThat(prompt).contains("每个[知识来源N]均标注了来源文件名、分块序号与内容摘录");
        assertThat(prompt).contains("只有某句话的内容确实来自某个知识片段时");
        assertThat(prompt).contains("不得集中标注在段落或回答的末尾");
        assertThat(prompt).contains("未实际使用的内容不得标注为引用");
        // 引用规则必须与知识片段之间用换行分隔，不能粘连
        assertThat(prompt).contains("未实际使用的内容不得标注为引用。\n[知识来源1：handbook.txt，分块 0]");
    }
}
