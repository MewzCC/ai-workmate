package com.aiworkmate.controller;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.common.Result;
import com.aiworkmate.dto.ApprovalDecisionRequest;
import com.aiworkmate.dto.AssetLedgerRequest;
import com.aiworkmate.dto.AssetLedgerResponse;
import com.aiworkmate.dto.AssetOperationRequest;
import com.aiworkmate.dto.MeetingRoomRequest;
import com.aiworkmate.dto.MeetingRoomResponse;
import com.aiworkmate.dto.SealUsageRequest;
import com.aiworkmate.dto.SealUsageResponse;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.dto.VisitorBookingRequest;
import com.aiworkmate.dto.VisitorBookingResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.AdminAssetsService;
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

/**
 * 行政资产 REST 接口。
 *
 * <p>四个子模块共享 {@code /api/admin-assets} 前缀：
 * <ul>
 *   <li>资产台账 {@code /assets} — 管理员 CRUD，需 {@code asset:write}。</li>
 *   <li>会议室 {@code /meeting-rooms} — 管理员 CRUD，需 {@code meeting:write}。</li>
 *   <li>访客预约 {@code /visitor-bookings} — 员工提交 + 直属上级审批。</li>
 *   <li>印章用印 {@code /seal-usages} — 员工提交 + 直属上级审批。</li>
 * </ul>
 *
 * <p>所有接口由 {@link com.aiworkmate.config.SecurityConfig} 默认要求 JWT 认证，
 * 业务权限由 Service 层按当前认证 {@code userId} 实时解析。
 */
@RestController
@RequestMapping("/api/admin-assets")
@RequiredArgsConstructor
public class AdminAssetsController {

    private final AdminAssetsService service;

    // ==================== 资产台账 ====================

    @GetMapping("/assets")
    public Result<PageResponse<AssetLedgerResponse>> listAssets(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.listAssets(user.userId(), keyword, category, status, page, size));
    }

    @GetMapping("/assets/{id}")
    public Result<AssetLedgerResponse> getAsset(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return Result.ok(service.getAsset(user.userId(), id));
    }

    @PostMapping("/assets")
    public Result<AssetLedgerResponse> createAsset(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AssetLedgerRequest request) {
        return Result.ok(service.createAsset(user.userId(), request));
    }

    @PutMapping("/assets/{id}")
    public Result<AssetLedgerResponse> updateAsset(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody AssetLedgerRequest request) {
        return Result.ok(service.updateAsset(user.userId(), id, request));
    }

    @DeleteMapping("/assets/{id}")
    public Result<Void> deleteAsset(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        service.deleteAsset(user.userId(), id);
        return Result.ok();
    }

    @PostMapping("/assets/{id}/claim")
    public Result<AssetLedgerResponse> claimAsset(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody AssetOperationRequest request) {
        return Result.ok(service.claimAsset(user.userId(), id, request));
    }

    @PostMapping("/assets/{id}/return")
    public Result<AssetLedgerResponse> returnAsset(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody AssetOperationRequest request) {
        return Result.ok(service.returnAsset(user.userId(), id, request));
    }

    @PostMapping("/assets/{id}/transfer")
    public Result<AssetLedgerResponse> transferAsset(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody AssetOperationRequest request) {
        return Result.ok(service.transferAsset(user.userId(), id, request));
    }

    // ==================== 会议室 ====================

    @GetMapping("/meeting-rooms")
    public Result<PageResponse<MeetingRoomResponse>> listMeetingRooms(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.listMeetingRooms(user.userId(), keyword, status, page, size));
    }

    @GetMapping("/meeting-rooms/{id}")
    public Result<MeetingRoomResponse> getMeetingRoom(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return Result.ok(service.getMeetingRoom(user.userId(), id));
    }

    @PostMapping("/meeting-rooms")
    public Result<MeetingRoomResponse> createMeetingRoom(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody MeetingRoomRequest request) {
        return Result.ok(service.createMeetingRoom(user.userId(), request));
    }

    @PutMapping("/meeting-rooms/{id}")
    public Result<MeetingRoomResponse> updateMeetingRoom(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody MeetingRoomRequest request) {
        return Result.ok(service.updateMeetingRoom(user.userId(), id, request));
    }

    @DeleteMapping("/meeting-rooms/{id}")
    public Result<Void> deleteMeetingRoom(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        service.deleteMeetingRoom(user.userId(), id);
        return Result.ok();
    }

    // ==================== 访客预约 ====================

    @PostMapping("/visitor-bookings")
    public Result<VisitorBookingResponse> submitVisitorBooking(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody VisitorBookingRequest request) {
        return Result.ok(service.submitVisitorBooking(user.userId(), request));
    }

    @GetMapping("/visitor-bookings/{id}")
    public Result<VisitorBookingResponse> getVisitorBooking(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return Result.ok(service.getVisitorBooking(user.userId(), id));
    }

    @GetMapping("/visitor-bookings/mine")
    public Result<PageResponse<VisitorBookingResponse>> myVisitorBookings(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.listMyVisitorBookings(user.userId(), status, page, size));
    }

    @GetMapping("/visitor-bookings/pending")
    public Result<PageResponse<VisitorBookingResponse>> pendingVisitorBookings(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.listPendingVisitorBookings(user.userId(), page, size));
    }

    @PostMapping("/visitor-bookings/{id}/withdraw")
    public Result<VisitorBookingResponse> withdrawVisitorBooking(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody VersionRequest request) {
        return Result.ok(service.withdrawVisitorBooking(user.userId(), id, request));
    }

    @PostMapping("/visitor-bookings/tasks/{taskId}/approve")
    public Result<VisitorBookingResponse> approveVisitorBooking(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long taskId,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        return Result.ok(service.approveVisitorBooking(user.userId(), taskId, request));
    }

    @PostMapping("/visitor-bookings/tasks/{taskId}/reject")
    public Result<VisitorBookingResponse> rejectVisitorBooking(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long taskId,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        return Result.ok(service.rejectVisitorBooking(user.userId(), taskId, request));
    }

    // ==================== 印章用印 ====================

    @PostMapping("/seal-usages")
    public Result<SealUsageResponse> submitSealUsage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SealUsageRequest request) {
        return Result.ok(service.submitSealUsage(user.userId(), request));
    }

    @GetMapping("/seal-usages/{id}")
    public Result<SealUsageResponse> getSealUsage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return Result.ok(service.getSealUsage(user.userId(), id));
    }

    @GetMapping("/seal-usages/mine")
    public Result<PageResponse<SealUsageResponse>> mySealUsages(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.listMySealUsages(user.userId(), status, page, size));
    }

    @GetMapping("/seal-usages/pending")
    public Result<PageResponse<SealUsageResponse>> pendingSealUsages(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.listPendingSealUsages(user.userId(), page, size));
    }

    @PostMapping("/seal-usages/{id}/withdraw")
    public Result<SealUsageResponse> withdrawSealUsage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody VersionRequest request) {
        return Result.ok(service.withdrawSealUsage(user.userId(), id, request));
    }

    @PostMapping("/seal-usages/tasks/{taskId}/approve")
    public Result<SealUsageResponse> approveSealUsage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long taskId,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        return Result.ok(service.approveSealUsage(user.userId(), taskId, request));
    }

    @PostMapping("/seal-usages/tasks/{taskId}/reject")
    public Result<SealUsageResponse> rejectSealUsage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long taskId,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        return Result.ok(service.rejectSealUsage(user.userId(), taskId, request));
    }
}
