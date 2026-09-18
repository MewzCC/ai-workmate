package com.aiworkmate.service;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.AssetLedgerRequest;
import com.aiworkmate.dto.AssetLedgerResponse;
import com.aiworkmate.dto.AssetInventoryRequest;
import com.aiworkmate.dto.AssetMaintenanceRequest;
import com.aiworkmate.dto.AssetOperationRequest;
import com.aiworkmate.dto.MeetingRoomRequest;
import com.aiworkmate.dto.MeetingRoomResponse;
import com.aiworkmate.dto.SealUsageRequest;
import com.aiworkmate.dto.SealUsageResponse;
import com.aiworkmate.dto.SealUseRequest;
import com.aiworkmate.dto.SealReturnRequest;
import com.aiworkmate.dto.VisitorBookingRequest;
import com.aiworkmate.dto.VisitorBookingResponse;
import com.aiworkmate.dto.VisitorVisitActionRequest;
import com.aiworkmate.dto.ApprovalDecisionRequest;
import com.aiworkmate.service.model.AssetAgentClaimCommand;
import com.aiworkmate.service.model.AssetAgentClaimReceipt;
import com.aiworkmate.service.model.AssetAgentReturnCommand;
import com.aiworkmate.service.model.AssetAgentReturnReceipt;
import com.aiworkmate.service.model.VisitorAgentApplicationCommand;
import com.aiworkmate.service.model.VisitorAgentApplicationReceipt;
import com.aiworkmate.service.model.VisitorAgentVisitCommand;
import com.aiworkmate.service.model.VisitorAgentVisitReceipt;
import com.aiworkmate.service.model.SealAgentApplicationCommand;
import com.aiworkmate.service.model.SealAgentApplicationReceipt;
import com.aiworkmate.service.model.SealAgentUseCommand;
import com.aiworkmate.service.model.SealAgentUseReceipt;
import com.aiworkmate.service.model.AssetAgentRepairStartCommand;
import com.aiworkmate.service.model.AssetAgentRepairStartReceipt;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 行政资产领域服务。
 *
 * <p>覆盖四个子模块：
 * <ul>
 *   <li>资产台账（asset-ledger）：admin 维护的简单 CRUD，无审批。</li>
 *   <li>会议室（meeting-room）：admin 维护的基础数据 CRUD；预订/审批能力后续阶段补充。</li>
 *   <li>访客预约（visitor-booking）：员工提交，直属上级审批（走通用 workflow）。</li>
 *   <li>印章用印（seal-usage）：员工提交，直属上级审批（走通用 workflow）。</li>
 * </ul>
 *
 * <p>所有接口按当前认证 {@code userId} 解析租户与归属，普通员工仅能查看/操作自己的申请，
 * 管理员（{@code asset:write} / {@code meeting:write}）可维护资产/会议室主数据。
 */
public interface AdminAssetsService {

    // ---------- 资产台账 ----------

    PageResponse<AssetLedgerResponse> listAssets(Long userId, String keyword, String category,
                                                  String status, int page, int size);

    AssetLedgerResponse getAsset(Long userId, Long id);

    AssetLedgerResponse createAsset(Long userId, AssetLedgerRequest request);

    AssetLedgerResponse updateAsset(Long userId, Long id, AssetLedgerRequest request);

    void deleteAsset(Long userId, Long id);

    AssetLedgerResponse claimAsset(Long userId, Long id, AssetOperationRequest request);

    AssetAgentClaimReceipt claimAssetAgent(
            Long userId, AssetAgentClaimCommand command, String operationKey);

    Optional<AssetAgentClaimReceipt> findAgentAssetClaim(
            Long userId, AssetAgentClaimCommand command, String operationKey);

    AssetAgentReturnReceipt returnAssetAgent(
            Long userId, AssetAgentReturnCommand command, String operationKey);

    Optional<AssetAgentReturnReceipt> findAgentAssetReturn(
            Long userId, AssetAgentReturnCommand command, String operationKey);

    AssetAgentRepairStartReceipt startAssetRepairAgent(
            Long userId, AssetAgentRepairStartCommand command, String operationKey);

    Optional<AssetAgentRepairStartReceipt> findAgentAssetRepairStart(
            Long userId, AssetAgentRepairStartCommand command, String operationKey);

    AssetLedgerResponse returnAsset(Long userId, Long id, AssetOperationRequest request);

    AssetLedgerResponse transferAsset(Long userId, Long id, AssetOperationRequest request);

    AssetLedgerResponse startAssetRepair(Long userId, Long id, AssetMaintenanceRequest request);

    AssetLedgerResponse completeAssetRepair(Long userId, Long id, AssetMaintenanceRequest request);

    AssetLedgerResponse inventoryAsset(Long userId, Long id, AssetInventoryRequest request);

    AssetLedgerResponse scrapAsset(Long userId, Long id, AssetMaintenanceRequest request);

    // ---------- 会议室 ----------

    PageResponse<MeetingRoomResponse> listMeetingRooms(Long userId, String keyword, String status,
                                                        int page, int size);

    MeetingRoomResponse getMeetingRoom(Long userId, Long id);

    MeetingRoomResponse createMeetingRoom(Long userId, MeetingRoomRequest request);

    MeetingRoomResponse updateMeetingRoom(Long userId, Long id, MeetingRoomRequest request);

    void deleteMeetingRoom(Long userId, Long id);

    // ---------- 访客预约 ----------

    VisitorBookingResponse submitVisitorBooking(Long userId, VisitorBookingRequest request);

    VisitorAgentApplicationReceipt submitVisitorBookingAgent(
            Long userId, VisitorAgentApplicationCommand command, String operationKey);

    Optional<VisitorAgentApplicationReceipt> findAgentVisitorBooking(
            Long userId, VisitorAgentApplicationCommand command, String operationKey);

    VisitorAgentVisitReceipt checkInVisitorAgent(
            Long userId, VisitorAgentVisitCommand command, String operationKey);

    Optional<VisitorAgentVisitReceipt> findAgentVisitorCheckIn(
            Long userId, VisitorAgentVisitCommand command, String operationKey);

    VisitorAgentVisitReceipt markVisitorArrivedAgent(
            Long userId, VisitorAgentVisitCommand command, String operationKey);

    Optional<VisitorAgentVisitReceipt> findAgentVisitorArrival(
            Long userId, VisitorAgentVisitCommand command, String operationKey);

    VisitorAgentVisitReceipt leaveVisitorAgent(
            Long userId, VisitorAgentVisitCommand command, String operationKey);

    Optional<VisitorAgentVisitReceipt> findAgentVisitorLeave(
            Long userId, VisitorAgentVisitCommand command, String operationKey);

    VisitorBookingResponse getVisitorBooking(Long userId, Long id);

    PageResponse<VisitorBookingResponse> listMyVisitorBookings(Long userId, String status,
                                                               int page, int size);

    PageResponse<VisitorBookingResponse> listPendingVisitorBookings(Long userId, int page, int size);

    VisitorBookingResponse withdrawVisitorBooking(Long userId, Long id, com.aiworkmate.dto.VersionRequest request);

    VisitorBookingResponse approveVisitorBooking(Long userId, Long taskId, ApprovalDecisionRequest request);

    VisitorBookingResponse rejectVisitorBooking(Long userId, Long taskId, ApprovalDecisionRequest request);

    VisitorBookingResponse checkInVisitor(Long userId, Long id, VisitorVisitActionRequest request);

    VisitorBookingResponse markVisitorArrived(Long userId, Long id, VisitorVisitActionRequest request);

    VisitorBookingResponse leaveVisitor(Long userId, Long id, VisitorVisitActionRequest request);

    VisitorBookingResponse markVisitorNoShow(Long userId, Long id, VisitorVisitActionRequest request);

    // ---------- 印章用印 ----------

    SealUsageResponse submitSealUsage(Long userId, SealUsageRequest request);
    SealAgentApplicationReceipt submitSealUsageAgent(
            Long userId, SealAgentApplicationCommand command, String operationKey);
    Optional<SealAgentApplicationReceipt> findAgentSealUsage(
            Long userId, SealAgentApplicationCommand command, String operationKey);
    SealAgentUseReceipt registerSealUseAgent(
            Long userId, SealAgentUseCommand command, String operationKey);
    Optional<SealAgentUseReceipt> findAgentRegisteredSealUse(
            Long userId, SealAgentUseCommand command, String operationKey);
    SealUsageResponse getSealUsage(Long userId, Long id);
    PageResponse<SealUsageResponse> listMySealUsages(Long userId, String status, int page, int size);
    PageResponse<SealUsageResponse> listPendingSealUsages(Long userId, int page, int size);
    SealUsageResponse withdrawSealUsage(Long userId, Long id, com.aiworkmate.dto.VersionRequest request);

    SealUsageResponse approveSealUsage(Long userId, Long taskId, ApprovalDecisionRequest request);

    SealUsageResponse rejectSealUsage(Long userId, Long taskId, ApprovalDecisionRequest request);

    SealUsageResponse registerSealUse(Long userId, Long id, SealUseRequest request);

    SealUsageResponse returnSeal(Long userId, Long id, SealReturnRequest request);

    /** 用于资产台账原值列与币种格式化参考；当前返回 CNY。 */
    default String assetCurrency() {
        return "CNY";
    }

    /** 默认原值占位，避免 null 影响 Response。 */
    default BigDecimal defaultOriginalValue() {
        return BigDecimal.ZERO;
    }
}
