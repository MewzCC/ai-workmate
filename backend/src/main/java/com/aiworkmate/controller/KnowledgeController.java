package com.aiworkmate.controller;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.common.Result;
import com.aiworkmate.dto.EmbeddingStatusResponse;
import com.aiworkmate.dto.KnowledgeDocumentBatchRequest;
import com.aiworkmate.dto.KnowledgeDocumentCreateRequest;
import com.aiworkmate.dto.KnowledgeDocumentDetailResponse;
import com.aiworkmate.dto.KnowledgeDocumentResponse;
import com.aiworkmate.dto.KnowledgeSearchRequest;
import com.aiworkmate.dto.KnowledgeSearchResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.KnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @GetMapping("/embedding-status")
    public Result<EmbeddingStatusResponse> embeddingStatus() {
        return Result.ok(knowledgeService.embeddingStatus());
    }

    @PostMapping("/documents")
    public Result<KnowledgeDocumentResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody KnowledgeDocumentCreateRequest request) {
        return Result.ok(knowledgeService.create(user.userId(), request));
    }

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeDocumentResponse> upload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("kbId") Long kbId,
            @RequestParam("file") MultipartFile file) {
        return Result.ok(knowledgeService.upload(user.userId(), kbId, file));
    }

    @PostMapping("/documents/{documentId}/reindex")
    public Result<KnowledgeDocumentResponse> reindex(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long documentId) {
        return Result.ok(knowledgeService.reindex(user.userId(), documentId));
    }

    @PostMapping("/documents/batch-delete")
    public Result<Integer> batchDelete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody KnowledgeDocumentBatchRequest request) {
        return Result.ok(knowledgeService.batchDelete(user.userId(), request.ids()));
    }

    @PostMapping("/documents/batch-reindex")
    public Result<List<KnowledgeDocumentResponse>> batchReindex(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody KnowledgeDocumentBatchRequest request) {
        return Result.ok(knowledgeService.batchReindex(user.userId(), request.ids()));
    }

    @GetMapping("/documents")
    public Result<PageResponse<KnowledgeDocumentResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long kbId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(knowledgeService.list(user.userId(), kbId, page, size));
    }

    @GetMapping("/documents/{documentId}")
    public Result<KnowledgeDocumentDetailResponse> detail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long documentId) {
        return Result.ok(knowledgeService.documentDetail(user.userId(), documentId));
    }

    @DeleteMapping("/documents/{documentId}")
    public Result<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long documentId) {
        knowledgeService.delete(user.userId(), documentId);
        return Result.ok();
    }

    @DeleteMapping("/documents/{documentId}/chunks/{chunkId}")
    public Result<Void> deleteChunk(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long documentId,
            @PathVariable Long chunkId) {
        knowledgeService.deleteChunk(user.userId(), documentId, chunkId);
        return Result.ok();
    }

    @PostMapping("/search")
    public Result<KnowledgeSearchResponse> search(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody KnowledgeSearchRequest request) {
        return Result.ok(knowledgeService.search(user.userId(), request));
    }

    @PostMapping("/bases/{kbId}/search")
    public Result<KnowledgeSearchResponse> searchInKnowledgeBase(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long kbId,
            @Valid @RequestBody KnowledgeSearchRequest request) {
        return Result.ok(knowledgeService.searchInKnowledgeBase(user.userId(), kbId, request));
    }
}
