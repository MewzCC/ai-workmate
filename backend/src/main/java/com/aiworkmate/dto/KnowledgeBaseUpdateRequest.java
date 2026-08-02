package com.aiworkmate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record KnowledgeBaseUpdateRequest(
        @Size(max = 80, message = "知识库名称不能超过 80 个字符")
        String name,

        @Size(max = 40, message = "图标标识不能超过 40 个字符")
        String icon,

        @Size(max = 500, message = "描述不能超过 500 个字符")
        String description,

        @Min(value = 100, message = "分块大小不能小于 100")
        @Max(value = 8000, message = "分块大小不能大于 8000")
        Integer chunkSize,

        @Min(value = 0, message = "分块重叠不能小于 0")
        @Max(value = 4000, message = "分块重叠不能大于 4000")
        Integer chunkOverlap,

        @Min(value = 1, message = "稠密检索数量不能小于 1")
        @Max(value = 50, message = "稠密检索数量不能大于 50")
        Integer denseTopK,

        @Min(value = 0, message = "稀疏检索数量不能小于 0")
        @Max(value = 50, message = "稀疏检索数量不能大于 50")
        Integer sparseTopK
) {
}
