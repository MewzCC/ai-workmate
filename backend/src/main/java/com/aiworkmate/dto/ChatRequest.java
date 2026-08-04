package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {

    /** 对话 ID，新对话为空 */
    @NotNull(message = "{validation.conversationId.notNull}")
    private Long conversationId;

    @NotBlank(message = "{validation.message.notBlank}")
    private String message;

    /** 模型名称，默认从配置读取 */
    private String model;

    /** 指定检索的知识库 ID；为空时检索当前用户全部知识库 */
    private Long kbId;

    @Size(max = 10, message = "{validation.attachmentIds.maxSize}")
    private List<Long> attachmentIds = List.of();

    @Min(value = 1, message = "{validation.maxContextRounds.min}")
    @Max(value = 20, message = "{validation.maxContextRounds.max}")
    private int maxContextRounds = 10;
}
