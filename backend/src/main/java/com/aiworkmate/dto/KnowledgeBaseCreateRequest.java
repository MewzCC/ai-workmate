package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeBaseCreateRequest(
        @NotBlank(message = "知识库名称不能为空")
        @Size(max = 80, message = "知识库名称不能超过 80 个字符")
        String name,

        @Size(max = 40, message = "图标标识不能超过 40 个字符")
        String icon,

        @Size(max = 500, message = "描述不能超过 500 个字符")
        String description
) {
}
