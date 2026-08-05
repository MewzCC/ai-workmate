package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.AiRuntimeProperties;
import com.aiworkmate.entity.Attachment;
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
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
                1001L, "USER", 2001L, "你好", "deepseek-chat", null, List.of(), 10,
                Locale.SIMPLIFIED_CHINESE))
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
                1001L, "USER", 2001L, "你好", "unknown-model", null, List.of(), 10,
                Locale.SIMPLIFIED_CHINESE))
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
                String.class, KnowledgeContext.class, List.class, boolean.class, Locale.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        String prompt = (String) method.invoke(chatService, "USER", knowledge, List.of(), false,
                Locale.SIMPLIFIED_CHINESE);

        assertThat(prompt).contains("像学术论文引用一样精确标注来源");
        assertThat(prompt).contains("每个[知识来源N]均标注了来源文件名、分块序号与内容摘录");
        assertThat(prompt).contains("只有某句话的内容确实来自某个知识片段时");
        assertThat(prompt).contains("不得集中标注在段落或回答的末尾");
        assertThat(prompt).contains("未实际使用的内容不得标注为引用");
        // 引用规则必须与知识片段之间用换行分隔，不能粘连
        assertThat(prompt).contains("未实际使用的内容不得标注为引用。\n[知识来源1：handbook.txt，分块 0]");
    }

    @Test
    void shouldRejectImageWithoutOcrTextForTextOnlyModel() {
        Conversation conversation = new Conversation();
        conversation.setId(2001L);
        conversation.setUserId(1001L);
        when(conversationMapper.selectOne(any())).thenReturn(conversation);
        when(aiRuntimeProperties.configured()).thenReturn(true);
        Attachment image = new Attachment();
        image.setType("image");
        image.setName("scan.png");
        image.setExtractedText(null);
        when(attachmentService.requireOwned(eq(1001L), eq(2001L), anyList())).thenReturn(List.of(image));
        when(knowledgeContextService.retrieve(eq(1001L), any(), eq(null))).thenReturn(KnowledgeContext.empty());

        assertThatThrownBy(() -> chatService.chat(
                1001L, "USER", 2001L, "帮我看这张图", "deepseek-chat", null, List.of(99L), 10,
                Locale.SIMPLIFIED_CHINESE))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.OCR_CAPABILITY_UNAVAILABLE.getErrorCode()));
    }

    @Test
    void shouldInjectOcrTextForTextOnlyModelAndSkipForMultimodal() throws Exception {
        Attachment image = new Attachment();
        image.setType("image");
        image.setName("scan.png");
        image.setExtractedText("请假一天，事假");
        List<Attachment> attachments = List.of(image);
        KnowledgeContext knowledge = KnowledgeContext.empty();

        var method = ChatServiceImpl.class.getDeclaredMethod("buildSystemPrompt",
                String.class, KnowledgeContext.class, List.class, boolean.class, Locale.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        String textOnlyPrompt = (String) method.invoke(chatService, "USER", knowledge, attachments, false,
                Locale.SIMPLIFIED_CHINESE);
        assertThat(textOnlyPrompt).contains("附件：scan.png").contains("请假一天，事假");

        @SuppressWarnings("unchecked")
        String multimodalPrompt = (String) method.invoke(chatService, "USER", knowledge, attachments, true,
                Locale.SIMPLIFIED_CHINESE);
        assertThat(multimodalPrompt).doesNotContain("请假一天，事假");
    }

    @Test
    void shouldInjectUiLanguageIntoSystemPrompt() throws Exception {
        var method = ChatServiceImpl.class.getDeclaredMethod("buildSystemPrompt",
                String.class, KnowledgeContext.class, List.class, boolean.class, Locale.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        String zhPrompt = (String) method.invoke(chatService, "USER", KnowledgeContext.empty(), List.of(), false,
                Locale.SIMPLIFIED_CHINESE);
        assertThat(zhPrompt).contains("当前用户界面语言：简体中文（zh-CN）");
        assertThat(zhPrompt).contains("除非用户明确要求使用其他语言，否则必须始终使用该语言回答");

        @SuppressWarnings("unchecked")
        String enPrompt = (String) method.invoke(chatService, "USER", KnowledgeContext.empty(), List.of(), false,
                Locale.US);
        assertThat(enPrompt).contains("当前用户界面语言：English (en-US)");
    }
}
