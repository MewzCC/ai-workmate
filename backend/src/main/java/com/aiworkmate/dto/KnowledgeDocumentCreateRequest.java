package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeDocumentCreateRequest(
        @NotBlank(message = "文件名不能为空")
        @Size(max = 255, message = "文件名不能超过 255 个字符")
        String filename,

        @NotBlank(message = "知识内容不能为空")
        @Size(max = 120000, message = "知识内容不能超过 120000 个字符")
        String content
) {
}
