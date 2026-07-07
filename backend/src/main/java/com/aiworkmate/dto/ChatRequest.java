package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

    /** 对话 ID，新对话为空 */
    private Long conversationId;

    @NotBlank(message = "消息内容不能为空")
    private String message;

    /** 模型名称，默认从配置读取 */
    private String model;
}
