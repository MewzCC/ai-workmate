package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.KnowledgeBaseCreateRequest;
import com.aiworkmate.dto.KnowledgeBaseResponse;
import com.aiworkmate.dto.KnowledgeBaseUpdateRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.KnowledgeBaseService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge/bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping
    public Result<List<KnowledgeBaseResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(knowledgeBaseService.list(user.userId()));
    }

    @PostMapping
    public Result<KnowledgeBaseResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody KnowledgeBaseCreateRequest request) {
        return Result.ok(knowledgeBaseService.create(user.userId(), request));
    }

    @GetMapping("/{kbId}")
    public Result<KnowledgeBaseResponse> detail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long kbId) {
        return Result.ok(knowledgeBaseService.detail(user.userId(), kbId));
    }

    @PutMapping("/{kbId}")
    public Result<KnowledgeBaseResponse> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long kbId,
            @Valid @RequestBody KnowledgeBaseUpdateRequest request) {
        return Result.ok(knowledgeBaseService.update(user.userId(), kbId, request));
    }

    @DeleteMapping("/{kbId}")
    public Result<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long kbId) {
        knowledgeBaseService.delete(user.userId(), kbId);
        return Result.ok();
    }
}
