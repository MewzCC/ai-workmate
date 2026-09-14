package com.aiworkmate.dto;

import java.util.List;

public record DictionaryItemPageResponse(List<DictionaryItemResponse> records, long total,
                                         int page, int size, boolean canManage) {
}
