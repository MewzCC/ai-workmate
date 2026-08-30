package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.SealUsageDocumentResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.SealUsageDocumentService;
import com.aiworkmate.service.model.SealUsageDocumentContent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/admin-assets/seal-usages/{sealUsageId}/documents")
@RequiredArgsConstructor
public class SealUsageDocumentController {
    private final SealUsageDocumentService documentService;

    @GetMapping
    public Result<List<SealUsageDocumentResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long sealUsageId) {
        return Result.ok(documentService.list(actor, sealUsageId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<SealUsageDocumentResponse> upload(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long sealUsageId,
            @RequestParam MultipartFile file) {
        return Result.ok(documentService.upload(actor, sealUsageId, file));
    }

    @GetMapping("/{documentId}/content")
    public ResponseEntity<org.springframework.core.io.Resource> content(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long sealUsageId,
            @PathVariable Long documentId) {
        SealUsageDocumentContent content = documentService.download(actor, sealUsageId, documentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(content.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(content.resource());
    }
}
