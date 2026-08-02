package com.aiworkmate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record KnowledgeDocumentBatchRequest(
        @NotEmpty(message = "请选择至少一个文档")
        @Size(max = 100, message = "单次批量操作最多支持 100 个文档")
        List<@NotNull(message = "文档 ID 不能为空") Long> ids
) {
}
