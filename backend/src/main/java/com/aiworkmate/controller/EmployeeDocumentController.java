package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.EmployeeDocumentResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.EmployeeDocumentService;
import com.aiworkmate.service.model.EmployeeDocumentContent;
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
@RequestMapping("/api/hr/employees/{employeeUserId}/documents")
@RequiredArgsConstructor
public class EmployeeDocumentController {
    private final EmployeeDocumentService documentService;

    @GetMapping
    public Result<List<EmployeeDocumentResponse>> list(
            @PathVariable Long employeeUserId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return Result.ok(documentService.list(actor, employeeUserId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<EmployeeDocumentResponse> upload(
            @PathVariable Long employeeUserId,
            @RequestParam String documentType,
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return Result.ok(documentService.upload(actor, employeeUserId, documentType, file));
    }

    @GetMapping("/{documentId}/content")
    public ResponseEntity<org.springframework.core.io.Resource> content(
            @PathVariable Long employeeUserId,
            @PathVariable Long documentId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        EmployeeDocumentContent content = documentService.download(actor, employeeUserId, documentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(content.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(content.resource());
    }
}
