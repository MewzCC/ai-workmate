package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.ApprovalApplicationAttachmentResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.ApprovalApplicationAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/approval-applications/{applicationId}/attachments")
public class ApprovalApplicationAttachmentController {
    private final ApprovalApplicationAttachmentService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ApprovalApplicationAttachmentResponse> upload(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long applicationId, @RequestParam String fieldName, @RequestParam MultipartFile file) {
        return Result.ok(service.upload(user.userId(), applicationId, fieldName, file));
    }

    @GetMapping("/{attachmentId}")
    public Result<ApprovalApplicationAttachmentResponse> metadata(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long applicationId, @PathVariable Long attachmentId,
            @RequestParam(required = false) Long taskId) {
        return Result.ok(service.metadata(user.userId(), applicationId, attachmentId, taskId));
    }

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<Resource> content(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long applicationId, @PathVariable Long attachmentId,
            @RequestParam(required = false) Long taskId) {
        var content = service.content(user.userId(), applicationId, attachmentId, taskId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.filename(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff").body(content.resource());
    }
}
