package com.aiworkmate.dto;

public record PositionResponse(
        Long id,
        String code,
        String name,
        Integer status
) {
}
