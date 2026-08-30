package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.common.TraceContext;
import com.aiworkmate.dto.ApprovalDecisionRequest;
import com.aiworkmate.dto.AssetLedgerRequest;
import com.aiworkmate.dto.AssetLedgerResponse;
import com.aiworkmate.dto.AssetInventoryRequest;
import com.aiworkmate.dto.AssetMaintenanceRequest;
import com.aiworkmate.dto.AssetOperationRequest;
import com.aiworkmate.dto.AssetOperationResponse;
import com.aiworkmate.dto.DepartmentResponse;
import com.aiworkmate.dto.MeetingRoomRequest;
import com.aiworkmate.dto.MeetingRoomResponse;
import com.aiworkmate.dto.SealUsageRequest;
import com.aiworkmate.dto.SealUsageResponse;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.dto.VisitorBookingRequest;
import com.aiworkmate.dto.VisitorBookingResponse;
import com.aiworkmate.dto.VisitorVisitActionRequest;
import com.aiworkmate.entity.AssetLedger;
import com.aiworkmate.entity.AssetOperation;
import com.aiworkmate.entity.MeetingRoom;
import com.aiworkmate.entity.MeetingBooking;
import com.aiworkmate.entity.SealUsage;
import com.aiworkmate.entity.User;
import com.aiworkmate.entity.VisitorBooking;
import com.aiworkmate.entity.WorkflowActionLog;
import com.aiworkmate.entity.WorkflowInstance;
import com.aiworkmate.entity.WorkflowTask;
import com.aiworkmate.mapper.AssetLedgerMapper;
import com.aiworkmate.mapper.AssetOperationMapper;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.MeetingRoomMapper;
import com.aiworkmate.mapper.MeetingBookingMapper;
import com.aiworkmate.mapper.SealUsageMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.mapper.VisitorBookingMapper;
import com.aiworkmate.mapper.WorkflowActionLogMapper;
import com.aiworkmate.mapper.WorkflowInstanceMapper;
import com.aiworkmate.mapper.WorkflowTaskMapper;
import com.aiworkmate.service.AdminAssetsService;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 行政资产领域服务实现。
 *
 * <p>资产台账、会议室为简单 CRUD（软删除）；访客预约、印章用印接入通用 workflow，
 * 状态机与 {@link com.aiworkmate.service.impl.LeaveWorkflowServiceImpl} 一致：
 * {@code PENDING -> APPROVED / REJECTED / WITHDRAWN}，由 WorkflowInstance + WorkflowTask 驱动。
 */
@Service
@RequiredArgsConstructor
public class AdminAssetsServiceImpl implements AdminAssetsService {

    private static final String BUSINESS_VISITOR = "VISITOR_BOOKING";
    private static final String BUSINESS_SEAL = "SEAL_USAGE";

    private final AssetLedgerMapper assetMapper;
    private final AssetOperationMapper assetOperationMapper;
    private final AccessControlMapper accessControlMapper;
    private final MeetingRoomMapper meetingRoomMapper;
    private final MeetingBookingMapper meetingBookingMapper;
    private final VisitorBookingMapper visitorMapper;
    private final SealUsageMapper sealMapper;
    private final UserMapper userMapper;
    private final WorkflowInstanceMapper instanceMapper;
    private final WorkflowTaskMapper taskMapper;
    private final WorkflowActionLogMapper actionLogMapper;
    private final UserAccessService userAccessService;
    private final BusinessAuditService auditService;
    private final NotificationService notificationService;

    @Value("${app.workflow.approval-due-hours:48}")
    private long approvalDueHours;

    // ============================================================
    // 资产台账
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AssetLedgerResponse> listAssets(Long userId, String keyword, String category,
                                                         String status, int page, int size) {
        ResolvedUserAccess actor = requirePermission(userId, "assets:read");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        String kw = normalize(keyword);

        LambdaQueryWrapper<AssetLedger> q = new LambdaQueryWrapper<AssetLedger>()
                .eq(AssetLedger::getTenantId, actor.tenantId())
                .eq(AssetLedger::getDeleted, false);
        if (kw != null) {
            q.and(w -> w.like(AssetLedger::getAssetCode, kw)
                    .or().like(AssetLedger::getName, kw)
                    .or().like(AssetLedger::getSpecification, kw));
        }
        if (category != null && !category.isBlank()) {
            q.eq(AssetLedger::getCategory, category.trim());
        }
        if (status != null && !status.isBlank()) {
            q.eq(AssetLedger::getStatus, status.trim());
        }
        q.orderByDesc(AssetLedger::getCreatedAt).last("LIMIT " + safeSize + " OFFSET " + offset);
        List<AssetLedger> rows = assetMapper.selectList(q);

        LambdaQueryWrapper<AssetLedger> cq = new LambdaQueryWrapper<AssetLedger>()
                .eq(AssetLedger::getTenantId, actor.tenantId())
                .eq(AssetLedger::getDeleted, false);
        if (kw != null) {
            cq.and(w -> w.like(AssetLedger::getAssetCode, kw)
                    .or().like(AssetLedger::getName, kw)
                    .or().like(AssetLedger::getSpecification, kw));
        }
        if (category != null && !category.isBlank()) {
            cq.eq(AssetLedger::getCategory, category.trim());
        }
        if (status != null && !status.isBlank()) {
            cq.eq(AssetLedger::getStatus, status.trim());
        }
        long total = assetMapper.selectCount(cq);

        boolean canWrite = actor.permissions().contains("asset:write");
        Map<Long, String> ownerNames = batchUserNames(rows.stream()
                .map(AssetLedger::getOwnerUserId).filter(java.util.Objects::nonNull).toList());
        Map<Long, String> departmentNames = batchDepartmentNames(actor.tenantId());
        return PageResponse.of(
                rows.stream().map(a -> toAssetResponse(a, departmentNames.get(a.getDepartmentId()),
                        a.getOwnerUserId() == null ? null : ownerNames.get(a.getOwnerUserId()),
                        canWrite)).toList(),
                total, safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public AssetLedgerResponse getAsset(Long userId, Long id) {
        ResolvedUserAccess actor = requirePermission(userId, "assets:read");
        AssetLedger a = requireAsset(actor.tenantId(), id);
        return assetDetail(actor, a);
    }

    @Override
    @Transactional
    public AssetLedgerResponse createAsset(Long userId, AssetLedgerRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "asset:write");
        if (assetMapper.exists(new LambdaQueryWrapper<AssetLedger>()
                .eq(AssetLedger::getTenantId, actor.tenantId())
                .eq(AssetLedger::getAssetCode, request.assetCode().trim())
                .eq(AssetLedger::getDeleted, false))) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.asset.code.duplicate");
        }
        LocalDateTime now = LocalDateTime.now();
        AssetLedger a = new AssetLedger();
        a.setTenantId(actor.tenantId());
        a.setAssetCode(request.assetCode().trim());
        a.setName(request.name().trim());
        a.setCategory(request.category().trim());
        a.setSpecification(trim(request.specification()));
        a.setStatus("IDLE");
        a.setDepartmentId(request.departmentId());
        a.setOwnerUserId(null);
        a.setPurchaseDate(request.purchaseDate());
        a.setOriginalValue(request.originalValue() == null ? BigDecimal.ZERO : request.originalValue());
        a.setRemark(trim(request.remark()));
        a.setVersion(0);
        a.setDeleted(false);
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        assetMapper.insert(a);
        auditService.record(actor.tenantId(), actor.userId(), "ASSET_LEDGER",
                a.getId().toString(), "CREATE", "SUCCESS", "新增资产台账");
        return assetDetail(actor, a);
    }

    @Override
    @Transactional
    public AssetLedgerResponse updateAsset(Long userId, Long id, AssetLedgerRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "asset:write");
        AssetLedger a = requireAsset(actor.tenantId(), id);
        if (request.version() == null) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        if (assetMapper.exists(new LambdaQueryWrapper<AssetLedger>()
                .eq(AssetLedger::getTenantId, actor.tenantId())
                .eq(AssetLedger::getAssetCode, request.assetCode().trim())
                .eq(AssetLedger::getDeleted, false)
                .ne(AssetLedger::getId, id))) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.asset.code.duplicate");
        }
        int updated = assetMapper.update(null, new LambdaUpdateWrapper<AssetLedger>()
                .eq(AssetLedger::getId, id)
                .eq(AssetLedger::getTenantId, actor.tenantId())
                .eq(AssetLedger::getDeleted, false)
                .eq(AssetLedger::getVersion, request.version())
                .set(AssetLedger::getAssetCode, request.assetCode().trim())
                .set(AssetLedger::getName, request.name().trim())
                .set(AssetLedger::getCategory, request.category().trim())
                .set(AssetLedger::getSpecification, trim(request.specification()))
                .set(AssetLedger::getPurchaseDate, request.purchaseDate())
                .set(AssetLedger::getOriginalValue,
                        request.originalValue() == null ? BigDecimal.ZERO : request.originalValue())
                .set(AssetLedger::getRemark, trim(request.remark()))
                .set(AssetLedger::getUpdatedAt, LocalDateTime.now())
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        auditService.record(actor.tenantId(), actor.userId(), "ASSET_LEDGER",
                id.toString(), "UPDATE", "SUCCESS", "更新资产台账");
        return assetDetail(actor, requireAsset(actor.tenantId(), id));
    }

    @Override
    @Transactional
    public void deleteAsset(Long userId, Long id) {
        ResolvedUserAccess actor = requirePermission(userId, "asset:write");
        AssetLedger a = requireAsset(actor.tenantId(), id);
        if (!"IDLE".equals(a.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.asset.delete.invalid");
        }
        int updated = assetMapper.update(null, new LambdaUpdateWrapper<AssetLedger>()
                .eq(AssetLedger::getId, id)
                .eq(AssetLedger::getTenantId, actor.tenantId())
                .eq(AssetLedger::getDeleted, false)
                .set(AssetLedger::getDeleted, true)
                .set(AssetLedger::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        auditService.record(actor.tenantId(), actor.userId(), "ASSET_LEDGER",
                id.toString(), "DELETE", "SUCCESS", "删除资产台账");
    }

    @Override
    @Transactional
    public AssetLedgerResponse claimAsset(Long userId, Long id, AssetOperationRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "asset:write");
        AssetLedger asset = requireAsset(actor.tenantId(), id);
        if (!"IDLE".equals(asset.getStatus()) || request.targetOwnerUserId() == null
                || request.targetDepartmentId() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.asset.claim.invalid");
        }
        requireOwnerInDepartment(actor.tenantId(), request.targetOwnerUserId(), request.targetDepartmentId());
        applyAssetOperation(actor, asset, request.version(), request.reason(), "CLAIM", "IN_USE",
                request.targetDepartmentId(), request.targetOwnerUserId());
        return assetDetail(actor, requireAsset(actor.tenantId(), id));
    }

    @Override
    @Transactional
    public AssetLedgerResponse returnAsset(Long userId, Long id, AssetOperationRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "asset:write");
        AssetLedger asset = requireAsset(actor.tenantId(), id);
        if (!"IN_USE".equals(asset.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.asset.return.invalid");
        }
        applyAssetOperation(actor, asset, request.version(), request.reason(), "RETURN", "IDLE",
                asset.getDepartmentId(), null);
        return assetDetail(actor, requireAsset(actor.tenantId(), id));
    }

    @Override
    @Transactional
    public AssetLedgerResponse transferAsset(Long userId, Long id, AssetOperationRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "asset:write");
        AssetLedger asset = requireAsset(actor.tenantId(), id);
        if ((!"IDLE".equals(asset.getStatus()) && !"IN_USE".equals(asset.getStatus()))
                || request.targetDepartmentId() == null
                || request.targetDepartmentId().equals(asset.getDepartmentId())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.asset.transfer.invalid");
        }
        requireDepartment(actor.tenantId(), request.targetDepartmentId());
        Long targetOwner = request.targetOwnerUserId();
        if ("IN_USE".equals(asset.getStatus())) {
            if (targetOwner == null) {
                throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.asset.transfer.ownerRequired");
            }
            requireOwnerInDepartment(actor.tenantId(), targetOwner, request.targetDepartmentId());
        } else if (targetOwner != null) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.asset.transfer.idleOwnerInvalid");
        }
        applyAssetOperation(actor, asset, request.version(), request.reason(), "TRANSFER", asset.getStatus(),
                request.targetDepartmentId(), targetOwner);
        return assetDetail(actor, requireAsset(actor.tenantId(), id));
    }

    @Override
    @Transactional
    public AssetLedgerResponse startAssetRepair(Long userId, Long id, AssetMaintenanceRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "asset:write");
        AssetLedger asset = requireAsset(actor.tenantId(), id);
        if (!"IDLE".equals(asset.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.asset.repair.start.invalid");
        }
        applyAssetOperation(actor, asset, request.version(), request.reason(), "REPAIR_START", "REPAIRING",
                asset.getDepartmentId(), null);
        return assetDetail(actor, requireAsset(actor.tenantId(), id));
    }

    @Override
    @Transactional
    public AssetLedgerResponse completeAssetRepair(Long userId, Long id, AssetMaintenanceRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "asset:write");
        AssetLedger asset = requireAsset(actor.tenantId(), id);
        if (!"REPAIRING".equals(asset.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.asset.repair.complete.invalid");
        }
        applyAssetOperation(actor, asset, request.version(), request.reason(), "REPAIR_COMPLETE", "IDLE",
                asset.getDepartmentId(), null);
        return assetDetail(actor, requireAsset(actor.tenantId(), id));
    }

    @Override
    @Transactional
    public AssetLedgerResponse inventoryAsset(Long userId, Long id, AssetInventoryRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "asset:write");
        AssetLedger asset = requireAsset(actor.tenantId(), id);
        if ("SCRAPPED".equals(asset.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.asset.inventory.invalid");
        }
        validateInventory(actor.tenantId(), asset, request);
        int updated = assetMapper.update(null, new LambdaUpdateWrapper<AssetLedger>()
                .eq(AssetLedger::getId, asset.getId())
                .eq(AssetLedger::getTenantId, actor.tenantId())
                .eq(AssetLedger::getDeleted, false)
                .eq(AssetLedger::getVersion, request.version())
                .eq(AssetLedger::getStatus, asset.getStatus())
                .set(AssetLedger::getUpdatedAt, LocalDateTime.now())
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        AssetOperation operation = newAssetOperation(actor, asset, "INVENTORY", asset.getStatus(),
                asset.getDepartmentId(), asset.getOwnerUserId(), request.reason());
        operation.setInventoryResult(request.inventoryResult());
        operation.setActualStatus(request.actualStatus());
        operation.setActualDepartmentId(request.actualDepartmentId());
        operation.setActualOwnerUserId(request.actualOwnerUserId());
        assetOperationMapper.insert(operation);
        auditService.recordTransactional(actor.tenantId(), actor.userId(), "ASSET_LEDGER",
                asset.getId().toString(), "INVENTORY", "SUCCESS",
                "result=" + request.inventoryResult() + ",bookStatus=" + asset.getStatus()
                        + ",actualStatus=" + request.actualStatus()
                        + ",actualDepartmentId=" + request.actualDepartmentId()
                        + ",actualOwnerUserId=" + request.actualOwnerUserId());
        return assetDetail(actor, requireAsset(actor.tenantId(), id));
    }

    @Override
    @Transactional
    public AssetLedgerResponse scrapAsset(Long userId, Long id, AssetMaintenanceRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "asset:write");
        AssetLedger asset = requireAsset(actor.tenantId(), id);
        if (!"IDLE".equals(asset.getStatus()) && !"REPAIRING".equals(asset.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.asset.scrap.invalid");
        }
        applyAssetOperation(actor, asset, request.version(), request.reason(), "SCRAP", "SCRAPPED",
                asset.getDepartmentId(), null);
        return assetDetail(actor, requireAsset(actor.tenantId(), id));
    }

    private void applyAssetOperation(ResolvedUserAccess actor, AssetLedger asset,
                                     Integer version, String reason, String operationType,
                                     String targetStatus, Long targetDepartmentId, Long targetOwnerUserId) {
        int updated = assetMapper.update(null, new LambdaUpdateWrapper<AssetLedger>()
                .eq(AssetLedger::getId, asset.getId())
                .eq(AssetLedger::getTenantId, actor.tenantId())
                .eq(AssetLedger::getDeleted, false)
                .eq(AssetLedger::getVersion, version)
                .eq(AssetLedger::getStatus, asset.getStatus())
                .set(AssetLedger::getStatus, targetStatus)
                .set(AssetLedger::getDepartmentId, targetDepartmentId)
                .set(AssetLedger::getOwnerUserId, targetOwnerUserId)
                .set(AssetLedger::getUpdatedAt, LocalDateTime.now())
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        AssetOperation operation = newAssetOperation(actor, asset, operationType, targetStatus,
                targetDepartmentId, targetOwnerUserId, reason);
        assetOperationMapper.insert(operation);
        auditService.recordTransactional(actor.tenantId(), actor.userId(), "ASSET_LEDGER",
                asset.getId().toString(), operationType, "SUCCESS",
                "fromStatus=" + asset.getStatus() + ",toStatus=" + targetStatus
                        + ",fromDepartmentId=" + asset.getDepartmentId()
                        + ",toDepartmentId=" + targetDepartmentId
                        + ",fromOwnerUserId=" + asset.getOwnerUserId()
                        + ",toOwnerUserId=" + targetOwnerUserId);
    }

    private AssetOperation newAssetOperation(ResolvedUserAccess actor, AssetLedger asset,
                                             String operationType, String targetStatus,
                                             Long targetDepartmentId, Long targetOwnerUserId, String reason) {
        AssetOperation operation = new AssetOperation();
        operation.setTenantId(actor.tenantId());
        operation.setAssetId(asset.getId());
        operation.setOperationType(operationType);
        operation.setFromStatus(asset.getStatus());
        operation.setToStatus(targetStatus);
        operation.setFromDepartmentId(asset.getDepartmentId());
        operation.setToDepartmentId(targetDepartmentId);
        operation.setFromOwnerUserId(asset.getOwnerUserId());
        operation.setToOwnerUserId(targetOwnerUserId);
        operation.setOperatorUserId(actor.userId());
        operation.setReason(trim(reason));
        operation.setCreatedAt(LocalDateTime.now());
        return operation;
    }

    private void validateInventory(Long tenantId, AssetLedger asset, AssetInventoryRequest request) {
        if (request.actualDepartmentId() != null) {
            requireDepartment(tenantId, request.actualDepartmentId());
        }
        if (request.actualOwnerUserId() != null) {
            if (request.actualDepartmentId() == null) {
                throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                        "oa.asset.inventory.ownerDepartmentRequired");
            }
            requireOwnerInDepartment(tenantId, request.actualOwnerUserId(), request.actualDepartmentId());
        }
        boolean matches = Objects.equals(asset.getStatus(), request.actualStatus())
                && Objects.equals(asset.getDepartmentId(), request.actualDepartmentId())
                && Objects.equals(asset.getOwnerUserId(), request.actualOwnerUserId());
        if ("MATCH".equals(request.inventoryResult()) && !matches) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.asset.inventory.match.invalid");
        }
        if (!"MATCH".equals(request.inventoryResult()) && trim(request.reason()) == null) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.asset.inventory.reason.required");
        }
    }

    // ============================================================
    // 会议室
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MeetingRoomResponse> listMeetingRooms(Long userId, String keyword, String status,
                                                                int page, int size) {
        ResolvedUserAccess actor = requireAnyPermission(userId, "assets:read", "meeting:read:self");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        String kw = normalize(keyword);

        LambdaQueryWrapper<MeetingRoom> q = meetingListWrapper(actor.tenantId(), kw, status);
        q.orderByDesc(MeetingRoom::getCreatedAt).last("LIMIT " + safeSize + " OFFSET " + offset);
        List<MeetingRoom> rows = meetingRoomMapper.selectList(q);
        long total = meetingRoomMapper.selectCount(meetingListWrapper(actor.tenantId(), kw, status));
        boolean canWrite = actor.permissions().contains("meeting:write");
        return PageResponse.of(
                rows.stream().map(m -> toMeetingRoomResponse(m, canWrite)).toList(),
                total, safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingRoomResponse getMeetingRoom(Long userId, Long id) {
        ResolvedUserAccess actor = requireAnyPermission(userId, "assets:read", "meeting:read:self");
        MeetingRoom m = requireMeetingRoom(actor.tenantId(), id);
        return toMeetingRoomResponse(m, actor.permissions().contains("meeting:write"));
    }

    @Override
    @Transactional
    public MeetingRoomResponse createMeetingRoom(Long userId, MeetingRoomRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "meeting:write");
        if (meetingRoomMapper.exists(new LambdaQueryWrapper<MeetingRoom>()
                .eq(MeetingRoom::getTenantId, actor.tenantId())
                .eq(MeetingRoom::getCode, request.code().trim())
                .eq(MeetingRoom::getDeleted, false))) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.meeting.code.duplicate");
        }
        LocalDateTime now = LocalDateTime.now();
        MeetingRoom m = new MeetingRoom();
        m.setTenantId(actor.tenantId());
        m.setCode(request.code().trim());
        m.setName(request.name().trim());
        m.setLocation(trim(request.location()));
        m.setCapacity(request.capacity() == null ? 0 : request.capacity());
        m.setFacilities(trim(request.facilities()));
        m.setStatus(request.status() == null ? "OPEN" : request.status());
        m.setRemark(trim(request.remark()));
        m.setDeleted(false);
        m.setCreatedAt(now);
        m.setUpdatedAt(now);
        meetingRoomMapper.insert(m);
        auditService.record(actor.tenantId(), actor.userId(), "MEETING_ROOM",
                m.getId().toString(), "CREATE", "SUCCESS", "新增会议室");
        return toMeetingRoomResponse(m, true);
    }

    @Override
    @Transactional
    public MeetingRoomResponse updateMeetingRoom(Long userId, Long id, MeetingRoomRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "meeting:write");
        MeetingRoom current = requireMeetingRoom(actor.tenantId(), id);
        String targetStatus = request.status() == null ? "OPEN" : request.status();
        if (!"CLOSED".equals(current.getStatus()) && "CLOSED".equals(targetStatus)
                && hasActiveMeetingBooking(actor.tenantId(), id)) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.meeting.room.activeBookings");
        }
        if (meetingRoomMapper.exists(new LambdaQueryWrapper<MeetingRoom>()
                .eq(MeetingRoom::getTenantId, actor.tenantId())
                .eq(MeetingRoom::getCode, request.code().trim())
                .eq(MeetingRoom::getDeleted, false)
                .ne(MeetingRoom::getId, id))) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.meeting.code.duplicate");
        }
        int updated = meetingRoomMapper.update(null, new LambdaUpdateWrapper<MeetingRoom>()
                .eq(MeetingRoom::getId, id)
                .eq(MeetingRoom::getTenantId, actor.tenantId())
                .eq(MeetingRoom::getDeleted, false)
                .set(MeetingRoom::getCode, request.code().trim())
                .set(MeetingRoom::getName, request.name().trim())
                .set(MeetingRoom::getLocation, trim(request.location()))
                .set(MeetingRoom::getCapacity, request.capacity() == null ? 0 : request.capacity())
                .set(MeetingRoom::getFacilities, trim(request.facilities()))
                .set(MeetingRoom::getStatus, targetStatus)
                .set(MeetingRoom::getRemark, trim(request.remark()))
                .set(MeetingRoom::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        auditService.record(actor.tenantId(), actor.userId(), "MEETING_ROOM",
                id.toString(), "UPDATE", "SUCCESS", "更新会议室");
        return toMeetingRoomResponse(requireMeetingRoom(actor.tenantId(), id), true);
    }

    @Override
    @Transactional
    public void deleteMeetingRoom(Long userId, Long id) {
        ResolvedUserAccess actor = requirePermission(userId, "meeting:write");
        requireMeetingRoom(actor.tenantId(), id);
        if (hasActiveMeetingBooking(actor.tenantId(), id)) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.meeting.room.activeBookings");
        }
        int updated = meetingRoomMapper.update(null, new LambdaUpdateWrapper<MeetingRoom>()
                .eq(MeetingRoom::getId, id)
                .eq(MeetingRoom::getTenantId, actor.tenantId())
                .eq(MeetingRoom::getDeleted, false)
                .set(MeetingRoom::getDeleted, true)
                .set(MeetingRoom::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        auditService.record(actor.tenantId(), actor.userId(), "MEETING_ROOM",
                id.toString(), "DELETE", "SUCCESS", "删除会议室");
    }

    // ============================================================
    // 访客预约（接入 workflow）
    // ============================================================

    @Override
    @Transactional
    public VisitorBookingResponse submitVisitorBooking(Long userId, VisitorBookingRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "visitor:create");
        if (request.expectedLeaveAt() != null
                && request.expectedLeaveAt().isBefore(request.expectedVisitAt())) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID,
                    "validation.visitor.leaveBeforeVisit");
        }
        Long approverId = resolveApprover(actor);
        if (approverId == null) {
            auditService.record(actor.tenantId(), actor.userId(), BUSINESS_VISITOR,
                    "0", "SUBMIT", "FAILURE", "未配置有效审批人");
            throw new BusinessException(ErrorCode.APPROVER_NOT_CONFIGURED);
        }
        Long definitionId = visitorMapper.selectDefinitionId(actor.tenantId());
        if (definitionId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "error.VISITOR_DEFINITION_MISSING");
        }
        LocalDateTime now = LocalDateTime.now();
        VisitorBooking v = new VisitorBooking();
        v.setTenantId(actor.tenantId());
        v.setApplicantUserId(actor.userId());
        v.setApproverUserId(approverId);
        v.setVisitorName(request.visitorName().trim());
        v.setVisitorCompany(trim(request.visitorCompany()));
        v.setVisitorPhone(trim(request.visitorPhone()));
        v.setPurpose(request.purpose().trim());
        v.setHostUserId(request.hostUserId());
        v.setExpectedVisitAt(request.expectedVisitAt());
        v.setExpectedLeaveAt(request.expectedLeaveAt());
        v.setPlateNumber(trim(request.plateNumber()));
        v.setPartySize(request.partySize() == null ? 1 : request.partySize());
        v.setStatus("PENDING");
        v.setVersion(0);
        v.setSubmittedAt(now);
        v.setCreatedAt(now);
        v.setUpdatedAt(now);
        visitorMapper.insert(v);

        WorkflowInstance instance = newWorkflowInstance(actor, definitionId, BUSINESS_VISITOR, v.getId(), now);
        WorkflowTask task = newWorkflowTask(actor, instance.getId(), BUSINESS_VISITOR, v.getId(),
                approverId, now);
        visitorMapper.update(null, new LambdaUpdateWrapper<VisitorBooking>()
                .eq(VisitorBooking::getId, v.getId())
                .set(VisitorBooking::getWorkflowInstanceId, instance.getId()));
        v.setWorkflowInstanceId(instance.getId());
        insertAction(actor, instance.getId(), task.getId(), "SUBMIT", null, "PENDING", null);
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_VISITOR,
                v.getId().toString(), "SUBMIT", "SUCCESS", "提交访客预约");
        notificationService.publish(actor.tenantId(), approverId,
                NotificationService.TYPE_APPROVAL,
                "新的访客预约待审批", "员工提交了访客来访预约，请及时处理",
                "visitor", v.getId());
        return toVisitorResponse(actor, v, task);
    }

    @Override
    @Transactional(readOnly = true)
    public VisitorBookingResponse getVisitorBooking(Long userId, Long id) {
        ResolvedUserAccess actor = requirePermission(userId, "visitor:read:self");
        VisitorBooking v = requireVisitor(actor, id, false);
        WorkflowTask task = activeTask(actor.tenantId(), BUSINESS_VISITOR, id);
        return toVisitorResponse(actor, v, task);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VisitorBookingResponse> listMyVisitorBookings(Long userId, String status,
                                                                       int page, int size) {
        ResolvedUserAccess actor = requirePermission(userId, "visitor:read:self");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        String st = normalize(status);
        LambdaQueryWrapper<VisitorBooking> q = new LambdaQueryWrapper<VisitorBooking>()
                .eq(VisitorBooking::getTenantId, actor.tenantId())
                .and(w -> w.eq(VisitorBooking::getApplicantUserId, actor.userId())
                        .or().eq(VisitorBooking::getHostUserId, actor.userId()))
                .eq(st != null, VisitorBooking::getStatus, st)
                .orderByDesc(VisitorBooking::getCreatedAt)
                .last("LIMIT " + safeSize + " OFFSET " + offset);
        List<VisitorBooking> rows = visitorMapper.selectList(q);
        LambdaQueryWrapper<VisitorBooking> cq = new LambdaQueryWrapper<VisitorBooking>()
                .eq(VisitorBooking::getTenantId, actor.tenantId())
                .and(w -> w.eq(VisitorBooking::getApplicantUserId, actor.userId())
                        .or().eq(VisitorBooking::getHostUserId, actor.userId()))
                .eq(st != null, VisitorBooking::getStatus, st);
        long total = visitorMapper.selectCount(cq);
        return PageResponse.of(rows.stream()
                .map(v -> toVisitorResponse(actor, v,
                        activeTask(actor.tenantId(), BUSINESS_VISITOR, v.getId()))).toList(),
                total, safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VisitorBookingResponse> listPendingVisitorBookings(Long userId, int page, int size) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:act");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        LambdaQueryWrapper<VisitorBooking> q = new LambdaQueryWrapper<VisitorBooking>()
                .eq(VisitorBooking::getTenantId, actor.tenantId())
                .eq(VisitorBooking::getApproverUserId, actor.userId())
                .eq(VisitorBooking::getStatus, "PENDING")
                .orderByDesc(VisitorBooking::getSubmittedAt)
                .last("LIMIT " + safeSize + " OFFSET " + offset);
        List<VisitorBooking> rows = visitorMapper.selectList(q);
        LambdaQueryWrapper<VisitorBooking> cq = new LambdaQueryWrapper<VisitorBooking>()
                .eq(VisitorBooking::getTenantId, actor.tenantId())
                .eq(VisitorBooking::getApproverUserId, actor.userId())
                .eq(VisitorBooking::getStatus, "PENDING");
        long total = visitorMapper.selectCount(cq);
        return PageResponse.of(rows.stream()
                .map(v -> toVisitorResponse(actor, v,
                        activeTask(actor.tenantId(), BUSINESS_VISITOR, v.getId()))).toList(),
                total, safePage, safeSize);
    }

    @Override
    @Transactional
    public VisitorBookingResponse withdrawVisitorBooking(Long userId, Long id, VersionRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "visitor:withdraw");
        VisitorBooking v = requireVisitor(actor, id, true);
        if (!"PENDING".equals(v.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = visitorMapper.update(null, new LambdaUpdateWrapper<VisitorBooking>()
                .eq(VisitorBooking::getId, id)
                .eq(VisitorBooking::getTenantId, actor.tenantId())
                .eq(VisitorBooking::getApplicantUserId, actor.userId())
                .eq(VisitorBooking::getStatus, "PENDING")
                .eq(VisitorBooking::getVersion, request.version())
                .set(VisitorBooking::getStatus, "WITHDRAWN")
                .set(VisitorBooking::getCompletedAt, now)
                .set(VisitorBooking::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (updated != 1) {
            auditService.record(actor.tenantId(), actor.userId(), BUSINESS_VISITOR,
                    id.toString(), "WITHDRAW", "CONFLICT", "状态或版本冲突");
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        WorkflowTask task = activeTask(actor.tenantId(), BUSINESS_VISITOR, id);
        if (task != null) {
            taskMapper.update(null, new LambdaUpdateWrapper<WorkflowTask>()
                    .eq(WorkflowTask::getId, task.getId())
                    .eq(WorkflowTask::getStatus, "PENDING")
                    .set(WorkflowTask::getStatus, "CANCELLED")
                    .set(WorkflowTask::getCompletedAt, now)
                    .set(WorkflowTask::getUpdatedAt, now)
                    .setSql("version = version + 1"));
            completeInstance(v.getWorkflowInstanceId(), actor.tenantId(), "CANCELLED", now);
            insertAction(actor, v.getWorkflowInstanceId(), task.getId(),
                    "WITHDRAW", "PENDING", "WITHDRAWN", null);
        }
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_VISITOR,
                id.toString(), "WITHDRAW", "SUCCESS", "撤回访客预约");
        return toVisitorResponse(actor, visitorMapper.selectById(id), null);
    }

    @Override
    @Transactional
    public VisitorBookingResponse approveVisitorBooking(Long userId, Long taskId, ApprovalDecisionRequest request) {
        return decideVisitor(userId, taskId, request, true);
    }

    @Override
    @Transactional
    public VisitorBookingResponse rejectVisitorBooking(Long userId, Long taskId, ApprovalDecisionRequest request) {
        if (request.comment() == null || request.comment().isBlank()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID,
                    "validation.visitor.rejectReason.required");
        }
        return decideVisitor(userId, taskId, request, false);
    }

    @Override
    @Transactional
    public VisitorBookingResponse checkInVisitor(Long userId, Long id, VisitorVisitActionRequest request) {
        return transitionVisitorVisit(userId, id, request, "APPROVED", "CHECKED_IN", "CHECK_IN");
    }

    @Override
    @Transactional
    public VisitorBookingResponse markVisitorArrived(Long userId, Long id, VisitorVisitActionRequest request) {
        return transitionVisitorVisit(userId, id, request, "CHECKED_IN", "VISITED", "ARRIVE");
    }

    @Override
    @Transactional
    public VisitorBookingResponse leaveVisitor(Long userId, Long id, VisitorVisitActionRequest request) {
        return transitionVisitorVisit(userId, id, request, "VISITED", "LEFT", "LEAVE");
    }

    @Override
    @Transactional
    public VisitorBookingResponse markVisitorNoShow(Long userId, Long id, VisitorVisitActionRequest request) {
        return transitionVisitorVisit(userId, id, request, "APPROVED", "NO_SHOW", "NO_SHOW");
    }

    private VisitorBookingResponse transitionVisitorVisit(Long userId, Long id, VisitorVisitActionRequest request,
                                                           String expectedStatus, String targetStatus, String action) {
        ResolvedUserAccess actor = requirePermission(userId, "visitor:register");
        VisitorBooking visitor = requireVisitor(actor, id, false);
        boolean related = actor.userId().equals(visitor.getApplicantUserId())
                || actor.userId().equals(visitor.getHostUserId());
        if (!related && !actor.permissions().contains("visitor:register:any")) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();
        if (!expectedStatus.equals(visitor.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.visitor.visit.transition.invalid");
        }
        if ("NO_SHOW".equals(targetStatus) && visitor.getExpectedVisitAt() != null
                && now.isBefore(visitor.getExpectedVisitAt())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.visitor.visit.noShow.tooEarly");
        }
        LambdaUpdateWrapper<VisitorBooking> update = new LambdaUpdateWrapper<VisitorBooking>()
                .eq(VisitorBooking::getId, id)
                .eq(VisitorBooking::getTenantId, actor.tenantId())
                .eq(VisitorBooking::getStatus, expectedStatus)
                .eq(VisitorBooking::getVersion, request.version())
                .set(VisitorBooking::getStatus, targetStatus)
                .set(VisitorBooking::getRegisteredByUserId, actor.userId())
                .set(VisitorBooking::getUpdatedAt, now)
                .setSql("version = version + 1");
        switch (targetStatus) {
            case "CHECKED_IN" -> update.set(VisitorBooking::getCheckedInAt, now);
            case "VISITED" -> update.set(VisitorBooking::getVisitedAt, now);
            case "LEFT" -> update.set(VisitorBooking::getLeftAt, now);
            case "NO_SHOW" -> update.set(VisitorBooking::getNoShowAt, now);
            default -> throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID);
        }
        if (visitorMapper.update(null, update) != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        auditService.recordTransactional(actor.tenantId(), actor.userId(), BUSINESS_VISITOR,
                id.toString(), action, "SUCCESS",
                "fromStatus=" + expectedStatus + ",toStatus=" + targetStatus
                        + ",remark=" + trim(request.remark()));
        return toVisitorResponse(actor, visitorMapper.selectById(id), null);
    }

    private VisitorBookingResponse decideVisitor(Long userId, Long taskId,
                                                  ApprovalDecisionRequest request, boolean approved) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:act");
        WorkflowTask task = requireAssignedTask(actor, taskId, BUSINESS_VISITOR);
        VisitorBooking v = visitorMapper.selectById(task.getBusinessId());
        if (v == null || !actor.tenantId().equals(v.getTenantId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (actor.userId().equals(v.getApplicantUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN, "error.SELF_APPROVAL_FORBIDDEN");
        }
        LocalDateTime now = LocalDateTime.now();
        String nextStatus = approved ? "APPROVED" : "REJECTED";
        int taskUpdated = taskMapper.update(null, new LambdaUpdateWrapper<WorkflowTask>()
                .eq(WorkflowTask::getId, taskId)
                .eq(WorkflowTask::getTenantId, actor.tenantId())
                .eq(WorkflowTask::getAssigneeUserId, actor.userId())
                .eq(WorkflowTask::getStatus, "PENDING")
                .eq(WorkflowTask::getVersion, request.version())
                .set(WorkflowTask::getStatus, nextStatus)
                .set(WorkflowTask::getDecisionComment, normalize(request.comment()))
                .set(WorkflowTask::getCompletedAt, now)
                .set(WorkflowTask::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (taskUpdated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        int vUpdated = visitorMapper.update(null, new LambdaUpdateWrapper<VisitorBooking>()
                .eq(VisitorBooking::getId, v.getId())
                .eq(VisitorBooking::getTenantId, actor.tenantId())
                .eq(VisitorBooking::getStatus, "PENDING")
                .set(VisitorBooking::getStatus, nextStatus)
                .set(VisitorBooking::getCompletedAt, now)
                .set(VisitorBooking::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (vUpdated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        completeInstance(v.getWorkflowInstanceId(), actor.tenantId(), "COMPLETED", now);
        insertAction(actor, v.getWorkflowInstanceId(), taskId,
                approved ? "APPROVE" : "REJECT", "PENDING", nextStatus,
                normalize(request.comment()));
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_VISITOR,
                v.getId().toString(), approved ? "APPROVE" : "REJECT", "SUCCESS",
                approved ? "通过访客预约" : "退回访客预约");
        notificationService.publish(actor.tenantId(), v.getApplicantUserId(),
                NotificationService.TYPE_APPROVAL,
                approved ? "访客预约已通过" : "访客预约已被退回",
                approved ? "你的访客预约已通过审批" : "你的访客预约被退回，请查看原因",
                "visitor", v.getId());
        return toVisitorResponse(actor, visitorMapper.selectById(v.getId()), null);
    }

    // ============================================================
    // 印章用印（接入 workflow）
    // ============================================================

    @Override
    @Transactional
    public SealUsageResponse submitSealUsage(Long userId, SealUsageRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "seal:create");
        Long approverId = resolveApprover(actor);
        if (approverId == null) {
            auditService.record(actor.tenantId(), actor.userId(), BUSINESS_SEAL,
                    "0", "SUBMIT", "FAILURE", "未配置有效审批人");
            throw new BusinessException(ErrorCode.APPROVER_NOT_CONFIGURED);
        }
        Long definitionId = sealMapper.selectDefinitionId(actor.tenantId());
        if (definitionId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "error.SEAL_DEFINITION_MISSING");
        }
        LocalDateTime now = LocalDateTime.now();
        SealUsage s = new SealUsage();
        s.setTenantId(actor.tenantId());
        s.setApplicantUserId(actor.userId());
        s.setApproverUserId(approverId);
        s.setSealType(request.sealType() == null ? "OTHER" : request.sealType());
        s.setDocumentTitle(request.documentTitle().trim());
        s.setUsageReason(request.usageReason().trim());
        s.setCopies(request.copies() == null ? 1 : request.copies());
        s.setStatus("PENDING");
        s.setVersion(0);
        s.setSubmittedAt(now);
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        sealMapper.insert(s);

        WorkflowInstance instance = newWorkflowInstance(actor, definitionId, BUSINESS_SEAL, s.getId(), now);
        WorkflowTask task = newWorkflowTask(actor, instance.getId(), BUSINESS_SEAL, s.getId(),
                approverId, now);
        sealMapper.update(null, new LambdaUpdateWrapper<SealUsage>()
                .eq(SealUsage::getId, s.getId())
                .set(SealUsage::getWorkflowInstanceId, instance.getId()));
        s.setWorkflowInstanceId(instance.getId());
        insertAction(actor, instance.getId(), task.getId(), "SUBMIT", null, "PENDING", null);
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_SEAL,
                s.getId().toString(), "SUBMIT", "SUCCESS", "提交用印申请");
        notificationService.publish(actor.tenantId(), approverId,
                NotificationService.TYPE_APPROVAL,
                "新的用印申请待审批", "员工提交了印章使用申请，请及时处理",
                "seal", s.getId());
        return toSealResponse(actor, s, task);
    }

    @Override
    @Transactional(readOnly = true)
    public SealUsageResponse getSealUsage(Long userId, Long id) {
        ResolvedUserAccess actor = requirePermission(userId, "seal:read:self");
        SealUsage s = requireSeal(actor, id, false);
        WorkflowTask task = activeTask(actor.tenantId(), BUSINESS_SEAL, id);
        return toSealResponse(actor, s, task);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SealUsageResponse> listMySealUsages(Long userId, String status, int page, int size) {
        ResolvedUserAccess actor = requirePermission(userId, "seal:read:self");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        String st = normalize(status);
        LambdaQueryWrapper<SealUsage> q = new LambdaQueryWrapper<SealUsage>()
                .eq(SealUsage::getTenantId, actor.tenantId())
                .eq(SealUsage::getApplicantUserId, actor.userId())
                .eq(st != null, SealUsage::getStatus, st)
                .orderByDesc(SealUsage::getCreatedAt)
                .last("LIMIT " + safeSize + " OFFSET " + offset);
        List<SealUsage> rows = sealMapper.selectList(q);
        LambdaQueryWrapper<SealUsage> cq = new LambdaQueryWrapper<SealUsage>()
                .eq(SealUsage::getTenantId, actor.tenantId())
                .eq(SealUsage::getApplicantUserId, actor.userId())
                .eq(st != null, SealUsage::getStatus, st);
        long total = sealMapper.selectCount(cq);
        return PageResponse.of(rows.stream()
                .map(s -> toSealResponse(actor, s,
                        activeTask(actor.tenantId(), BUSINESS_SEAL, s.getId()))).toList(),
                total, safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SealUsageResponse> listPendingSealUsages(Long userId, int page, int size) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:act");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        LambdaQueryWrapper<SealUsage> q = new LambdaQueryWrapper<SealUsage>()
                .eq(SealUsage::getTenantId, actor.tenantId())
                .eq(SealUsage::getApproverUserId, actor.userId())
                .eq(SealUsage::getStatus, "PENDING")
                .orderByDesc(SealUsage::getSubmittedAt)
                .last("LIMIT " + safeSize + " OFFSET " + offset);
        List<SealUsage> rows = sealMapper.selectList(q);
        LambdaQueryWrapper<SealUsage> cq = new LambdaQueryWrapper<SealUsage>()
                .eq(SealUsage::getTenantId, actor.tenantId())
                .eq(SealUsage::getApproverUserId, actor.userId())
                .eq(SealUsage::getStatus, "PENDING");
        long total = sealMapper.selectCount(cq);
        return PageResponse.of(rows.stream()
                .map(s -> toSealResponse(actor, s,
                        activeTask(actor.tenantId(), BUSINESS_SEAL, s.getId()))).toList(),
                total, safePage, safeSize);
    }

    @Override
    @Transactional
    public SealUsageResponse withdrawSealUsage(Long userId, Long id, VersionRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "seal:withdraw");
        SealUsage s = requireSeal(actor, id, true);
        if (!"PENDING".equals(s.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = sealMapper.update(null, new LambdaUpdateWrapper<SealUsage>()
                .eq(SealUsage::getId, id)
                .eq(SealUsage::getTenantId, actor.tenantId())
                .eq(SealUsage::getApplicantUserId, actor.userId())
                .eq(SealUsage::getStatus, "PENDING")
                .eq(SealUsage::getVersion, request.version())
                .set(SealUsage::getStatus, "WITHDRAWN")
                .set(SealUsage::getCompletedAt, now)
                .set(SealUsage::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (updated != 1) {
            auditService.record(actor.tenantId(), actor.userId(), BUSINESS_SEAL,
                    id.toString(), "WITHDRAW", "CONFLICT", "状态或版本冲突");
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        WorkflowTask task = activeTask(actor.tenantId(), BUSINESS_SEAL, id);
        if (task != null) {
            taskMapper.update(null, new LambdaUpdateWrapper<WorkflowTask>()
                    .eq(WorkflowTask::getId, task.getId())
                    .eq(WorkflowTask::getStatus, "PENDING")
                    .set(WorkflowTask::getStatus, "CANCELLED")
                    .set(WorkflowTask::getCompletedAt, now)
                    .set(WorkflowTask::getUpdatedAt, now)
                    .setSql("version = version + 1"));
            completeInstance(s.getWorkflowInstanceId(), actor.tenantId(), "CANCELLED", now);
            insertAction(actor, s.getWorkflowInstanceId(), task.getId(),
                    "WITHDRAW", "PENDING", "WITHDRAWN", null);
        }
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_SEAL,
                id.toString(), "WITHDRAW", "SUCCESS", "撤回用印申请");
        return toSealResponse(actor, sealMapper.selectById(id), null);
    }

    @Override
    @Transactional
    public SealUsageResponse approveSealUsage(Long userId, Long taskId, ApprovalDecisionRequest request) {
        return decideSeal(userId, taskId, request, true);
    }

    @Override
    @Transactional
    public SealUsageResponse rejectSealUsage(Long userId, Long taskId, ApprovalDecisionRequest request) {
        if (request.comment() == null || request.comment().isBlank()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID,
                    "validation.seal.rejectReason.required");
        }
        return decideSeal(userId, taskId, request, false);
    }

    private SealUsageResponse decideSeal(Long userId, Long taskId,
                                          ApprovalDecisionRequest request, boolean approved) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:act");
        WorkflowTask task = requireAssignedTask(actor, taskId, BUSINESS_SEAL);
        SealUsage s = sealMapper.selectById(task.getBusinessId());
        if (s == null || !actor.tenantId().equals(s.getTenantId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (actor.userId().equals(s.getApplicantUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN, "error.SELF_APPROVAL_FORBIDDEN");
        }
        LocalDateTime now = LocalDateTime.now();
        String nextStatus = approved ? "APPROVED" : "REJECTED";
        int taskUpdated = taskMapper.update(null, new LambdaUpdateWrapper<WorkflowTask>()
                .eq(WorkflowTask::getId, taskId)
                .eq(WorkflowTask::getTenantId, actor.tenantId())
                .eq(WorkflowTask::getAssigneeUserId, actor.userId())
                .eq(WorkflowTask::getStatus, "PENDING")
                .eq(WorkflowTask::getVersion, request.version())
                .set(WorkflowTask::getStatus, nextStatus)
                .set(WorkflowTask::getDecisionComment, normalize(request.comment()))
                .set(WorkflowTask::getCompletedAt, now)
                .set(WorkflowTask::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (taskUpdated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        int sUpdated = sealMapper.update(null, new LambdaUpdateWrapper<SealUsage>()
                .eq(SealUsage::getId, s.getId())
                .eq(SealUsage::getTenantId, actor.tenantId())
                .eq(SealUsage::getStatus, "PENDING")
                .set(SealUsage::getStatus, nextStatus)
                .set(SealUsage::getCompletedAt, now)
                .set(SealUsage::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (sUpdated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        completeInstance(s.getWorkflowInstanceId(), actor.tenantId(), "COMPLETED", now);
        insertAction(actor, s.getWorkflowInstanceId(), taskId,
                approved ? "APPROVE" : "REJECT", "PENDING", nextStatus,
                normalize(request.comment()));
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_SEAL,
                s.getId().toString(), approved ? "APPROVE" : "REJECT", "SUCCESS",
                approved ? "通过用印申请" : "退回用印申请");
        notificationService.publish(actor.tenantId(), s.getApplicantUserId(),
                NotificationService.TYPE_APPROVAL,
                approved ? "用印申请已通过" : "用印申请已被退回",
                approved ? "你的用印申请已通过审批" : "你的用印申请被退回，请查看原因",
                "seal", s.getId());
        return toSealResponse(actor, sealMapper.selectById(s.getId()), null);
    }

    // ============================================================
    // 共享私有辅助
    // ============================================================

    private ResolvedUserAccess requireAccess(Long userId) {
        ResolvedUserAccess access = userAccessService.resolveActiveUser(userId);
        if (access == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return access;
    }

    private ResolvedUserAccess requirePermission(Long userId, String permission) {
        ResolvedUserAccess access = requireAccess(userId);
        if (!access.permissions().contains(permission)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return access;
    }

    private ResolvedUserAccess requireAnyPermission(Long userId, String... permissions) {
        ResolvedUserAccess access = requireAccess(userId);
        for (String permission : permissions) {
            if (access.permissions().contains(permission)) return access;
        }
        throw new BusinessException(ErrorCode.PERMISSION_DENIED);
    }

    private AssetLedger requireAsset(Long tenantId, Long id) {
        AssetLedger a = assetMapper.selectById(id);
        if (a == null || !tenantId.equals(a.getTenantId()) || Boolean.TRUE.equals(a.getDeleted())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return a;
    }

    private void requireDepartment(Long tenantId, Long departmentId) {
        if (accessControlMapper.countDepartment(tenantId, departmentId) != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void requireOwnerInDepartment(Long tenantId, Long ownerUserId, Long departmentId) {
        requireDepartment(tenantId, departmentId);
        User owner = userMapper.selectById(ownerUserId);
        if (owner == null || !tenantId.equals(owner.getTenantId())
                || !Integer.valueOf(1).equals(owner.getStatus())
                || !departmentId.equals(owner.getDepartmentId())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.asset.owner.invalid");
        }
    }

    private MeetingRoom requireMeetingRoom(Long tenantId, Long id) {
        MeetingRoom m = meetingRoomMapper.selectById(id);
        if (m == null || !tenantId.equals(m.getTenantId()) || Boolean.TRUE.equals(m.getDeleted())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return m;
    }

    private VisitorBooking requireVisitor(ResolvedUserAccess actor, Long id, boolean requireOwned) {
        VisitorBooking v = visitorMapper.selectById(id);
        if (v == null || !actor.tenantId().equals(v.getTenantId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (requireOwned && !actor.userId().equals(v.getApplicantUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        return v;
    }

    private SealUsage requireSeal(ResolvedUserAccess actor, Long id, boolean requireOwned) {
        SealUsage s = sealMapper.selectById(id);
        if (s == null || !actor.tenantId().equals(s.getTenantId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (requireOwned && !actor.userId().equals(s.getApplicantUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        return s;
    }

    private WorkflowTask requireAssignedTask(ResolvedUserAccess actor, Long taskId, String businessType) {
        WorkflowTask task = taskMapper.selectById(taskId);
        if (task == null || !actor.tenantId().equals(task.getTenantId())
                || !businessType.equals(task.getBusinessType())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!actor.userId().equals(task.getAssigneeUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        if (!"PENDING".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID);
        }
        return task;
    }

    private WorkflowTask activeTask(Long tenantId, String businessType, Long businessId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<WorkflowTask>()
                .eq(WorkflowTask::getTenantId, tenantId)
                .eq(WorkflowTask::getBusinessType, businessType)
                .eq(WorkflowTask::getBusinessId, businessId)
                .eq(WorkflowTask::getStatus, "PENDING")
                .last("LIMIT 1"));
    }

    private void completeInstance(Long instanceId, Long tenantId, String status, LocalDateTime now) {
        int updated = instanceMapper.update(null, new LambdaUpdateWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getId, instanceId)
                .eq(WorkflowInstance::getTenantId, tenantId)
                .eq(WorkflowInstance::getStatus, "RUNNING")
                .set(WorkflowInstance::getStatus, status)
                .set(WorkflowInstance::getCompletedAt, now)
                .set(WorkflowInstance::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

    private void insertAction(ResolvedUserAccess actor, Long instanceId, Long taskId,
                              String action, String fromStatus, String toStatus, String comment) {
        WorkflowActionLog log = new WorkflowActionLog();
        log.setTenantId(actor.tenantId());
        log.setInstanceId(instanceId);
        log.setTaskId(taskId);
        log.setActorUserId(actor.userId());
        log.setAction(action);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setComment(comment);
        String traceId = TraceContext.traceId();
        log.setTraceId(traceId == null ? UUID.randomUUID().toString().replace("-", "") : traceId);
        log.setCreatedAt(LocalDateTime.now());
        actionLogMapper.insert(log);
    }

    private WorkflowInstance newWorkflowInstance(ResolvedUserAccess actor, Long definitionId,
                                                   String businessType, Long businessId, LocalDateTime now) {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setTenantId(actor.tenantId());
        instance.setDefinitionId(definitionId);
        instance.setBusinessType(businessType);
        instance.setBusinessId(businessId);
        instance.setApplicantId(actor.userId());
        instance.setStatus("RUNNING");
        instance.setVersion(0);
        instance.setStartedAt(now);
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);
        instanceMapper.insert(instance);
        return instance;
    }

    private WorkflowTask newWorkflowTask(ResolvedUserAccess actor, Long instanceId, String businessType,
                                          Long businessId, Long approverId, LocalDateTime now) {
        WorkflowTask task = new WorkflowTask();
        task.setTenantId(actor.tenantId());
        task.setInstanceId(instanceId);
        task.setBusinessType(businessType);
        task.setBusinessId(businessId);
        task.setAssigneeUserId(approverId);
        task.setStatus("PENDING");
        task.setVersion(0);
        task.setDueAt(approvalDueHours > 0 ? now.plusHours(approvalDueHours) : null);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        return task;
    }

    private Long resolveApprover(ResolvedUserAccess actor) {
        User applicant = userMapper.selectById(actor.userId());
        if (applicant == null || applicant.getApproverUserId() == null) {
            return null;
        }
        User approver = userMapper.selectById(applicant.getApproverUserId());
        if (approver == null || approver.getStatus() == null || approver.getStatus() != 1) {
            return null;
        }
        return approver.getId();
    }

    private Map<Long, String> batchUserNames(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<User> users = userMapper.selectBatchIds(ids);
        return users.stream().collect(Collectors.toMap(User::getId,
                u -> u.getDisplayName() != null && !u.getDisplayName().isBlank()
                        ? u.getDisplayName() : u.getUsername(),
                (a, b) -> a));
    }

    private Map<Long, String> batchDepartmentNames(Long tenantId) {
        return accessControlMapper.selectDepartments(tenantId).stream()
                .collect(Collectors.toMap(DepartmentResponse::id, DepartmentResponse::name, (a, b) -> a));
    }

    private String singleUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            return null;
        }
        return u.getDisplayName() != null && !u.getDisplayName().isBlank() ? u.getDisplayName() : u.getUsername();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private LambdaQueryWrapper<MeetingRoom> meetingListWrapper(Long tenantId, String keyword, String status) {
        LambdaQueryWrapper<MeetingRoom> q = new LambdaQueryWrapper<MeetingRoom>()
                .eq(MeetingRoom::getTenantId, tenantId)
                .eq(MeetingRoom::getDeleted, false);
        if (keyword != null) {
            q.and(w -> w.like(MeetingRoom::getCode, keyword)
                    .or().like(MeetingRoom::getName, keyword)
                    .or().like(MeetingRoom::getLocation, keyword));
        }
        if (status != null && !status.isBlank()) {
            q.eq(MeetingRoom::getStatus, status.trim());
        }
        return q;
    }

    private boolean hasActiveMeetingBooking(Long tenantId, Long roomId) {
        return meetingBookingMapper.exists(new LambdaQueryWrapper<MeetingBooking>()
                .eq(MeetingBooking::getTenantId, tenantId)
                .eq(MeetingBooking::getRoomId, roomId)
                .eq(MeetingBooking::getStatus, "BOOKED")
                .gt(MeetingBooking::getEndAt, LocalDateTime.now()));
    }

    private AssetLedgerResponse toAssetResponse(AssetLedger a, String departmentName,
                                                 String ownerName, boolean canWrite) {
        return toAssetResponse(a, departmentName, ownerName, canWrite, List.of());
    }

    private AssetLedgerResponse assetDetail(ResolvedUserAccess actor, AssetLedger asset) {
        Map<Long, String> departments = batchDepartmentNames(actor.tenantId());
        List<AssetOperation> operations = assetOperationMapper.selectList(
                new LambdaQueryWrapper<AssetOperation>()
                        .eq(AssetOperation::getTenantId, actor.tenantId())
                        .eq(AssetOperation::getAssetId, asset.getId())
                        .orderByDesc(AssetOperation::getCreatedAt)
                        .orderByDesc(AssetOperation::getId));
        HashSet<Long> userIds = new HashSet<>();
        for (AssetOperation operation : operations) {
            if (operation.getFromOwnerUserId() != null) userIds.add(operation.getFromOwnerUserId());
            if (operation.getToOwnerUserId() != null) userIds.add(operation.getToOwnerUserId());
            if (operation.getActualOwnerUserId() != null) userIds.add(operation.getActualOwnerUserId());
            userIds.add(operation.getOperatorUserId());
        }
        if (asset.getOwnerUserId() != null) userIds.add(asset.getOwnerUserId());
        Map<Long, String> users = batchUserNames(new ArrayList<>(userIds));
        List<AssetOperationResponse> history = operations.stream().map(operation ->
                new AssetOperationResponse(operation.getId(), operation.getOperationType(),
                        operation.getFromStatus(), operation.getToStatus(),
                        operation.getFromDepartmentId(), nullableLookup(departments, operation.getFromDepartmentId()),
                        operation.getToDepartmentId(), nullableLookup(departments, operation.getToDepartmentId()),
                        operation.getFromOwnerUserId(), nullableLookup(users, operation.getFromOwnerUserId()),
                        operation.getToOwnerUserId(), nullableLookup(users, operation.getToOwnerUserId()),
                        operation.getOperatorUserId(), nullableLookup(users, operation.getOperatorUserId()),
                        operation.getReason(), operation.getInventoryResult(), operation.getActualStatus(),
                        operation.getActualDepartmentId(), nullableLookup(departments, operation.getActualDepartmentId()),
                        operation.getActualOwnerUserId(), nullableLookup(users, operation.getActualOwnerUserId()),
                        operation.getCreatedAt())).toList();
        return toAssetResponse(asset, nullableLookup(departments, asset.getDepartmentId()),
                nullableLookup(users, asset.getOwnerUserId()), actor.permissions().contains("asset:write"), history);
    }

    private <T> String nullableLookup(Map<T, String> values, T key) {
        return key == null ? null : values.get(key);
    }

    private AssetLedgerResponse toAssetResponse(AssetLedger a, String departmentName,
                                                 String ownerName, boolean canWrite,
                                                 List<AssetOperationResponse> history) {
        return new AssetLedgerResponse(
                a.getId(), a.getAssetCode(), a.getName(), a.getCategory(), a.getSpecification(),
                a.getStatus(), a.getDepartmentId(), departmentName, a.getOwnerUserId(), ownerName,
                a.getPurchaseDate(), a.getOriginalValue(), a.getRemark(),
                a.getVersion(), history,
                a.getCreatedAt(), a.getUpdatedAt(), canWrite, canWrite && "IDLE".equals(a.getStatus()));
    }

    private MeetingRoomResponse toMeetingRoomResponse(MeetingRoom m, boolean canWrite) {
        return new MeetingRoomResponse(
                m.getId(), m.getCode(), m.getName(), m.getLocation(), m.getCapacity(),
                m.getFacilities(), m.getStatus(), m.getRemark(),
                m.getCreatedAt(), m.getUpdatedAt(), canWrite, canWrite);
    }

    private VisitorBookingResponse toVisitorResponse(ResolvedUserAccess actor, VisitorBooking v,
                                                      WorkflowTask task) {
        boolean applicant = actor.userId().equals(v.getApplicantUserId());
        boolean approver = actor.userId().equals(v.getApproverUserId());
        boolean canWithdraw = applicant && "PENDING".equals(v.getStatus())
                && actor.permissions().contains("visitor:withdraw");
        boolean canDecide = approver && "PENDING".equals(v.getStatus())
                && actor.permissions().contains("approval:act")
                && !applicant;
        boolean related = applicant || actor.userId().equals(v.getHostUserId());
        boolean canRegister = actor.permissions().contains("visitor:register")
                && (related || actor.permissions().contains("visitor:register:any"));
        boolean canCheckIn = canRegister && "APPROVED".equals(v.getStatus());
        boolean canMarkVisited = canRegister && "CHECKED_IN".equals(v.getStatus());
        boolean canLeave = canRegister && "VISITED".equals(v.getStatus());
        boolean canMarkNoShow = canCheckIn && v.getExpectedVisitAt() != null
                && !LocalDateTime.now().isBefore(v.getExpectedVisitAt());
        return new VisitorBookingResponse(
                v.getId(), v.getApplicantUserId(), singleUserName(v.getApplicantUserId()),
                v.getApproverUserId(), singleUserName(v.getApproverUserId()),
                v.getHostUserId(), singleUserName(v.getHostUserId()),
                v.getVisitorName(), v.getVisitorCompany(), v.getVisitorPhone(), v.getPurpose(),
                v.getExpectedVisitAt(), v.getExpectedLeaveAt(), v.getPlateNumber(), v.getPartySize(),
                v.getStatus(), v.getVersion(), v.getWorkflowInstanceId(),
                task == null ? null : task.getId(),
                task == null ? null : task.getVersion(),
                task == null ? null : task.getStatus(),
                v.getSubmittedAt(), v.getCompletedAt(),
                v.getRegisteredByUserId(), singleUserName(v.getRegisteredByUserId()),
                v.getCheckedInAt(), v.getVisitedAt(), v.getLeftAt(), v.getNoShowAt(),
                v.getCreatedAt(), v.getUpdatedAt(), canWithdraw, canDecide,
                canCheckIn, canMarkVisited, canLeave, canMarkNoShow);
    }

    private SealUsageResponse toSealResponse(ResolvedUserAccess actor, SealUsage s, WorkflowTask task) {
        boolean applicant = actor.userId().equals(s.getApplicantUserId());
        boolean approver = actor.userId().equals(s.getApproverUserId());
        boolean canWithdraw = applicant && "PENDING".equals(s.getStatus())
                && actor.permissions().contains("seal:withdraw");
        boolean canDecide = approver && "PENDING".equals(s.getStatus())
                && actor.permissions().contains("approval:act")
                && !applicant;
        return new SealUsageResponse(
                s.getId(), s.getApplicantUserId(), singleUserName(s.getApplicantUserId()),
                s.getApproverUserId(), singleUserName(s.getApproverUserId()),
                s.getSealType(), s.getDocumentTitle(), s.getUsageReason(), s.getCopies(),
                s.getStatus(), s.getVersion(), s.getWorkflowInstanceId(),
                task == null ? null : task.getId(),
                task == null ? null : task.getVersion(),
                task == null ? null : task.getStatus(),
                s.getSubmittedAt(), s.getCompletedAt(), s.getCreatedAt(), s.getUpdatedAt(),
                canWithdraw, canDecide);
    }
}
