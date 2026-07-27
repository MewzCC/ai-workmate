package com.aiworkmate.common;

import java.util.List;

public record PageResponse<T>(
        List<T> records,
        long total,
        int page,
        int size
) {
    public static <T> PageResponse<T> of(List<T> records, long total, int page, int size) {
        return new PageResponse<>(List.copyOf(records), total, page, size);
    }
}
