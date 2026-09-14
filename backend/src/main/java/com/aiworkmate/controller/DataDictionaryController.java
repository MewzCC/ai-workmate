package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.DictionaryItemPageResponse;
import com.aiworkmate.dto.DictionaryItemRequest;
import com.aiworkmate.dto.DictionaryItemResponse;
import com.aiworkmate.dto.DictionaryOptionResponse;
import com.aiworkmate.dto.DictionaryStatusRequest;
import com.aiworkmate.dto.DictionaryTypeListResponse;
import com.aiworkmate.dto.DictionaryTypeRequest;
import com.aiworkmate.dto.DictionaryTypeResponse;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.DataDictionaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DataDictionaryController {
    private final DataDictionaryService service;

    @GetMapping("/api/admin/dictionaries")
    public Result<DictionaryTypeListResponse> listTypes(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        return Result.ok(service.listTypes(user.userId(), keyword, status));
    }

    @PostMapping("/api/admin/dictionaries")
    public Result<DictionaryTypeResponse> createType(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody DictionaryTypeRequest request) {
        return Result.ok(service.createType(user.userId(), request));
    }

    @PutMapping("/api/admin/dictionaries/{id}")
    public Result<DictionaryTypeResponse> updateType(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id, @Valid @RequestBody DictionaryTypeRequest request) {
        return Result.ok(service.updateType(user.userId(), id, request));
    }

    @PutMapping("/api/admin/dictionaries/{id}/status")
    public Result<DictionaryTypeResponse> updateTypeStatus(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id, @Valid @RequestBody DictionaryStatusRequest request) {
        return Result.ok(service.updateTypeStatus(user.userId(), id, request));
    }

    @DeleteMapping("/api/admin/dictionaries/{id}")
    public Result<Void> deleteType(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        service.deleteType(user.userId(), id, request.version());
        return Result.ok();
    }

    @GetMapping("/api/admin/dictionaries/{id}/items")
    public Result<DictionaryItemPageResponse> listItems(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.listItems(user.userId(), id, keyword, status, page, size));
    }

    @PostMapping("/api/admin/dictionaries/{id}/items")
    public Result<DictionaryItemResponse> createItem(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id, @Valid @RequestBody DictionaryItemRequest request) {
        return Result.ok(service.createItem(user.userId(), id, request));
    }

    @PutMapping("/api/admin/dictionaries/{id}/items/{itemId}")
    public Result<DictionaryItemResponse> updateItem(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id, @PathVariable Long itemId, @Valid @RequestBody DictionaryItemRequest request) {
        return Result.ok(service.updateItem(user.userId(), id, itemId, request));
    }

    @PutMapping("/api/admin/dictionaries/{id}/items/{itemId}/status")
    public Result<DictionaryItemResponse> updateItemStatus(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id, @PathVariable Long itemId, @Valid @RequestBody DictionaryStatusRequest request) {
        return Result.ok(service.updateItemStatus(user.userId(), id, itemId, request));
    }

    @DeleteMapping("/api/admin/dictionaries/{id}/items/{itemId}")
    public Result<Void> deleteItem(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id, @PathVariable Long itemId, @Valid @RequestBody VersionRequest request) {
        service.deleteItem(user.userId(), id, itemId, request.version());
        return Result.ok();
    }

    @GetMapping("/api/dictionaries/{code}/items")
    public Result<List<DictionaryOptionResponse>> activeOptions(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String code) {
        return Result.ok(service.activeOptions(user.userId(), code));
    }
}
