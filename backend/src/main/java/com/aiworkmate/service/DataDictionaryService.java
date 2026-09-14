package com.aiworkmate.service;

import com.aiworkmate.dto.DictionaryItemPageResponse;
import com.aiworkmate.dto.DictionaryItemRequest;
import com.aiworkmate.dto.DictionaryItemResponse;
import com.aiworkmate.dto.DictionaryOptionResponse;
import com.aiworkmate.dto.DictionaryStatusRequest;
import com.aiworkmate.dto.DictionaryTypeListResponse;
import com.aiworkmate.dto.DictionaryTypeRequest;
import com.aiworkmate.dto.DictionaryTypeResponse;

import java.util.List;

public interface DataDictionaryService {
    DictionaryTypeListResponse listTypes(Long userId, String keyword, String status);
    DictionaryTypeResponse createType(Long userId, DictionaryTypeRequest request);
    DictionaryTypeResponse updateType(Long userId, Long id, DictionaryTypeRequest request);
    DictionaryTypeResponse updateTypeStatus(Long userId, Long id, DictionaryStatusRequest request);
    void deleteType(Long userId, Long id, Integer version);
    DictionaryItemPageResponse listItems(Long userId, Long typeId, String keyword, String status, int page, int size);
    DictionaryItemResponse createItem(Long userId, Long typeId, DictionaryItemRequest request);
    DictionaryItemResponse updateItem(Long userId, Long typeId, Long itemId, DictionaryItemRequest request);
    DictionaryItemResponse updateItemStatus(Long userId, Long typeId, Long itemId, DictionaryStatusRequest request);
    void deleteItem(Long userId, Long typeId, Long itemId, Integer version);
    List<DictionaryOptionResponse> activeOptions(Long userId, String code);
}
