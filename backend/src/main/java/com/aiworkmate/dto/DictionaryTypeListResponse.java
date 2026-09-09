package com.aiworkmate.dto;

import java.util.List;

public record DictionaryTypeListResponse(List<DictionaryTypeResponse> records, boolean canManage) {
}
