package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private String role;    // user / assistant / system

    private String content;

    private String status;

    private String feedback;

    /** 已验证的 Agent 任务来源；每个任务最多写入一条会话回复。 */
    private String sourceTaskNo;

    /** 已执行计划的首个固定工具，用于客户端提供受控业务页面入口。 */
    private String sourceToolCode;

    /** 引用知识库 JSON 数组字符串，例如 [{"docId":"1","chunkId":"2","source":"a.pdf","score":0.8,"text":"..."}] */
    private String citations;

    private Integer tokenCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
