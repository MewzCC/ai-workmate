package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.AttachmentResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.AttachmentService;
import com.aiworkmate.service.model.AttachmentContent;
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

@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<AttachmentResponse> upload(@RequestParam Long conversationId,
                                             @RequestParam MultipartFile file,
                                             @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(attachmentService.upload(user.userId(), conversationId, file));
    }

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<org.springframework.core.io.Resource> content(
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        AttachmentContent content = attachmentService.loadContent(user.userId(), attachmentId);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(content.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(content.resource());
    }
}
