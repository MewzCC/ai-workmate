package com.aiworkmate.controller;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.common.Result;
import com.aiworkmate.dto.AttendanceClockRequest;
import com.aiworkmate.dto.AttendanceClockResponse;
import com.aiworkmate.dto.AttendanceReissueDecisionRequest;
import com.aiworkmate.dto.AttendanceReissueRequest;
import com.aiworkmate.dto.AttendanceReissueResponse;
import com.aiworkmate.dto.AttendanceRecordResponse;
import com.aiworkmate.dto.AttendanceStatisticsResponse;
import com.aiworkmate.dto.AttendanceTodayStatusResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.AttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 考勤管理 REST 接口。
 *
 * <p>所有接口由 {@link com.aiworkmate.config.SecurityConfig} 默认要求 JWT 认证，
 * 任何登录用户均可访问；管理员视角（团队统计、全员异常）由 Service 层按角色判断。
 */
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/clock")
    public Result<AttendanceClockResponse> clock(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AttendanceClockRequest request,
            HttpServletRequest httpRequest) {
        return Result.ok(attendanceService.clock(user.userId(), request, clientIp(httpRequest)));
    }

    @GetMapping("/today-status")
    public Result<AttendanceTodayStatusResponse> todayStatus(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(attendanceService.getTodayStatus(user.userId()));
    }

    @GetMapping("/records")
    public Result<PageResponse<AttendanceRecordResponse>> records(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(attendanceService.listRecords(user.userId(), from, to, userId, page, size));
    }

    @GetMapping("/exceptions")
    public Result<PageResponse<AttendanceRecordResponse>> exceptions(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(attendanceService.listExceptions(user.userId(), from, to, userId, page, size));
    }

    @PostMapping("/reissue")
    public Result<AttendanceReissueResponse> submitReissue(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AttendanceReissueRequest request) {
        return Result.ok(attendanceService.submitReissue(user.userId(), request));
    }

    @GetMapping("/reissue/mine")
    public Result<PageResponse<AttendanceReissueResponse>> myReissues(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(attendanceService.listMyReissues(user.userId(), status, page, size));
    }

    @GetMapping("/reissue/pending")
    public Result<PageResponse<AttendanceReissueResponse>> pendingReissues(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(attendanceService.listPendingReissues(user.userId(), page, size));
    }

    @PostMapping("/reissue/{id}/decide")
    public Result<AttendanceReissueResponse> decideReissue(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody AttendanceReissueDecisionRequest request) {
        return Result.ok(attendanceService.decideReissue(user.userId(), id, request));
    }

    @GetMapping("/statistics")
    public Result<AttendanceStatisticsResponse> statistics(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return Result.ok(attendanceService.getStatistics(user.userId(), year, month));
    }

    /** 解析客户端 IP，优先取 X-Forwarded-For 首段。 */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
