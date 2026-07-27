package com.aiworkmate.dto;

public record DepartmentResponse(
        Long id,
        String code,
        String name,
        Long parentId,
        Long defaultApproverUserId,
        Integer status
) {
}
