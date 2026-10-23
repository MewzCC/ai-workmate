package com.aiworkmate.dto;

/** One tenant-scoped item and its verified parent; no identity or internal storage metadata. */
public record DictionaryItemDetailResponse(DictionaryTypeResponse dictionaryType, DictionaryItemResponse item) { }
