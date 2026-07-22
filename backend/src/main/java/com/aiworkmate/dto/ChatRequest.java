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
    @NotNull(message = "会话 ID 不能为空")
    private Long conversationId;

    @NotBlank(message = "消息内容不能为空")
    private String message;

    /** 模型名称，默认从配置读取 */
    private String model;

    @Size(max = 10, message = "单条消息最多包含 10 个附件")
    private List<Long> attachmentIds = List.of();

    @Min(value = 1, message = "上下文轮数不能小于 1")
    @Max(value = 20, message = "上下文轮数不能超过 20")
    private int maxContextRounds = 10;
}
