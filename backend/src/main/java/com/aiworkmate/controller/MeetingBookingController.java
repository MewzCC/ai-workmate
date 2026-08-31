package com.aiworkmate.controller;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.common.Result;
import com.aiworkmate.dto.MeetingBookingCancelRequest;
import com.aiworkmate.dto.MeetingBookingRequest;
import com.aiworkmate.dto.MeetingBookingResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.MeetingBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin-assets/meeting-bookings")
@RequiredArgsConstructor
public class MeetingBookingController {
    private final MeetingBookingService service;

    @PostMapping
    public Result<MeetingBookingResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @RequestBody MeetingBookingRequest request) {
        return Result.ok(service.create(user.userId(), request));
    }

    @GetMapping("/mine")
    public Result<PageResponse<MeetingBookingResponse>> listMine(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.listMine(user.userId(), from, to, status, page, size));
    }

    @GetMapping("/admin")
    public Result<PageResponse<MeetingBookingResponse>> listAdmin(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.listAdmin(user.userId(), roomId, from, to, status, page, size));
    }

    @PostMapping("/{id}/cancel")
    public Result<MeetingBookingResponse> cancel(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable Long id,
                                                  @Valid @RequestBody MeetingBookingCancelRequest request) {
        return Result.ok(service.cancel(user.userId(), id, request));
    }
}
