package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.EmployeeChangeDecisionRequest;
import com.aiworkmate.dto.EmployeeChangeRequest;
import com.aiworkmate.dto.EmployeeChangeResponse;
import com.aiworkmate.dto.EmployeeChangeView;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.entity.EmployeeChange;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.EmployeeChangeMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.EmployeeChangeService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EmployeeChangeServiceImpl implements EmployeeChangeService {
    private static final String RESOURCE_TYPE = "EMPLOYEE_CHANGE";
    private static final Set<String> TYPES = Set.of(
            "ONBOARDING", "REGULARIZATION", "TRANSFER", "OFFBOARDING");

    private final EmployeeChangeMapper changeMapper;
    private final UserMapper userMapper;
    private final AccessControlMapper accessControlMapper;
    private final UserAccessService userAccessService;
    private final BusinessAuditService auditService;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EmployeeChangeResponse> list(Long userId, String status, String changeType,
                                                     String keyword, int page, int size) {
        ResolvedUserAccess actor = requirePermission(userId, "hr:read");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String normalizedType = normalize(changeType);
        if (normalizedType != null && !TYPES.contains(normalizedType)) {
            throw invalid("validation.hr.change.typeInvalid");
        }
        List<EmployeeChangeResponse> records = changeMapper.selectPage(
                        actor.tenantId(), normalize(status), normalizedType, normalize(keyword),
                        safeSize, (safePage - 1) * safeSize)
                .stream().map(view -> response(actor, view)).toList();
        long total = changeMapper.countPage(
                actor.tenantId(), normalize(status), normalizedType, normalize(keyword));
        return PageResponse.of(records, total, safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeChangeResponse detail(Long userId, Long id) {
        ResolvedUserAccess actor = requirePermission(userId, "hr:read");
        return response(actor, requireView(actor.tenantId(), id));
    }

    @Override
    @Transactional
    public EmployeeChangeResponse create(Long userId, EmployeeChangeRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "hr:manage");
        User employee = requireTenantUser(actor.tenantId(), request.employeeUserId(), false);
        ResolvedUserAccess reviewer = requirePermission(request.reviewApproverUserId(), "hr:manage");
        if (!actor.tenantId().equals(reviewer.tenantId()) || actor.userId().equals(reviewer.userId())) {
            throw invalid("validation.hr.change.reviewerInvalid");
        }
        String type = request.changeType().trim();
        validateTarget(actor.tenantId(), employee.getId(), type,
                request.targetDepartmentId(), request.targetPositionId(), request.targetSupervisorUserId());
        long pending = changeMapper.selectCount(new LambdaQueryWrapper<EmployeeChange>()
                .eq(EmployeeChange::getTenantId, actor.tenantId())
                .eq(EmployeeChange::getEmployeeUserId, employee.getId())
                .eq(EmployeeChange::getChangeType, type)
                .eq(EmployeeChange::getStatus, "PENDING"));
        if (pending > 0) {
            throw invalid("validation.hr.change.duplicatePending");
        }

        LocalDateTime now = LocalDateTime.now();
        EmployeeChange change = new EmployeeChange();
        change.setTenantId(actor.tenantId());
        change.setEmployeeUserId(employee.getId());
        change.setApplicantUserId(actor.userId());
        change.setReviewApproverUserId(reviewer.userId());
        change.setChangeType(type);
        change.setEffectiveDate(request.effectiveDate());
        change.setCurrentDepartmentId(employee.getDepartmentId());
        change.setCurrentPositionId(employee.getPositionId());
        change.setCurrentSupervisorUserId(employee.getApproverUserId());
        change.setTargetDepartmentId(request.targetDepartmentId());
        change.setTargetPositionId(request.targetPositionId());
        change.setTargetSupervisorUserId(request.targetSupervisorUserId());
        change.setReason(request.reason().trim());
        change.setStatus("PENDING");
        change.setVersion(0);
        change.setSubmittedAt(now);
        change.setCreatedAt(now);
        change.setUpdatedAt(now);
        changeMapper.insert(change);

        auditService.recordTransactional(actor.tenantId(), actor.userId(), RESOURCE_TYPE,
                change.getId().toString(), "SUBMIT", "SUCCESS", "提交员工变动申请：" + type);
        notificationService.publish(actor.tenantId(), reviewer.userId(),
                NotificationService.TYPE_APPROVAL, "新的员工变动申请",
                "有一笔入转调离申请等待处理", "employee-change", change.getId());
        return response(actor, requireView(actor.tenantId(), change.getId()));
    }

    @Override
    @Transactional
    public EmployeeChangeResponse approve(Long userId, Long id, EmployeeChangeDecisionRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "hr:manage");
        EmployeeChange change = requirePending(actor, id, request.version(), true);
        User employee = requireTenantUser(actor.tenantId(), change.getEmployeeUserId(), false);
        if ("OFFBOARDING".equals(change.getChangeType())
                && accessControlMapper.selectUserRoleCodes(actor.tenantId(), employee.getId())
                .contains("SUPER_ADMIN")
                && accessControlMapper.countActiveSuperAdminsForTenant(actor.tenantId()) <= 1) {
            throw invalid("validation.hr.change.lastSuperAdmin");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean effectiveNow = !change.getEffectiveDate().isAfter(LocalDate.now());
        if (effectiveNow) {
            applyEmployeeState(actor.tenantId(), employee, change);
        }
        updateDecision(actor, change, request.version(), effectiveNow ? "EFFECTIVE" : "APPROVED",
                request.comment(), now, effectiveNow);
        auditService.recordTransactional(actor.tenantId(), actor.userId(), RESOURCE_TYPE,
                id.toString(), "APPROVE", "SUCCESS",
                effectiveNow ? "批准员工变动并更新员工状态" : "批准员工变动并等待生效日");
        notifyApplicant(actor, change, "员工变动申请已通过");
        return response(actor, requireView(actor.tenantId(), id));
    }

    @Override
    @Transactional
    public EmployeeChangeResponse reject(Long userId, Long id, EmployeeChangeDecisionRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "hr:manage");
        if (request.comment() == null || request.comment().isBlank()) {
            throw invalid("validation.hr.change.rejectCommentRequired");
        }
        EmployeeChange change = requirePending(actor, id, request.version(), true);
        updateDecision(actor, change, request.version(), "REJECTED", request.comment(), LocalDateTime.now(), false);
        auditService.recordTransactional(actor.tenantId(), actor.userId(), RESOURCE_TYPE,
                id.toString(), "REJECT", "SUCCESS", "驳回员工变动申请");
        notifyApplicant(actor, change, "员工变动申请已驳回");
        return response(actor, requireView(actor.tenantId(), id));
    }

    @Override
    @Transactional
    public EmployeeChangeResponse withdraw(Long userId, Long id, VersionRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "hr:manage");
        EmployeeChange change = requirePending(actor, id, request.version(), false);
        if (!actor.userId().equals(change.getApplicantUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = changeMapper.update(null, new LambdaUpdateWrapper<EmployeeChange>()
                .eq(EmployeeChange::getId, id)
                .eq(EmployeeChange::getTenantId, actor.tenantId())
                .eq(EmployeeChange::getStatus, "PENDING")
                .eq(EmployeeChange::getVersion, request.version())
                .set(EmployeeChange::getStatus, "WITHDRAWN")
                .set(EmployeeChange::getWithdrawnAt, now)
                .set(EmployeeChange::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (updated != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        auditService.recordTransactional(actor.tenantId(), actor.userId(), RESOURCE_TYPE,
                id.toString(), "WITHDRAW", "SUCCESS", "撤回员工变动申请");
        return response(actor, requireView(actor.tenantId(), id));
    }

    private void validateTarget(Long tenantId, Long employeeId, String type,
                                Long departmentId, Long positionId, Long supervisorId) {
        if (!TYPES.contains(type)) throw invalid("validation.hr.change.typeInvalid");
        if ("ONBOARDING".equals(type) || "TRANSFER".equals(type)) {
            if (departmentId == null || positionId == null
                    || accessControlMapper.countDepartment(tenantId, departmentId) == 0
                    || accessControlMapper.countPosition(tenantId, positionId) == 0) {
                throw invalid("validation.hr.change.targetRequired");
            }
            if (supervisorId != null) {
                if (employeeId.equals(supervisorId)) throw invalid("validation.hr.change.supervisorSelf");
                requireTenantUser(tenantId, supervisorId, true);
            }
        }
    }

    private EmployeeChange requirePending(ResolvedUserAccess actor, Long id,
                                          Integer version, boolean requireReviewer) {
        EmployeeChange change = changeMapper.selectOne(new LambdaQueryWrapper<EmployeeChange>()
                .eq(EmployeeChange::getId, id)
                .eq(EmployeeChange::getTenantId, actor.tenantId()));
        if (change == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!"PENDING".equals(change.getStatus())) throw invalid("validation.hr.change.stateInvalid");
        if (!version.equals(change.getVersion())) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        if (requireReviewer && !actor.userId().equals(change.getReviewApproverUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        return change;
    }

    private void applyEmployeeState(Long tenantId, User employee, EmployeeChange change) {
        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>()
                .eq(User::getId, employee.getId())
                .eq(User::getTenantId, tenantId)
                .set(User::getUpdatedAt, LocalDateTime.now());
        switch (change.getChangeType()) {
            case "ONBOARDING" -> update
                    .set(User::getStatus, 1)
                    .set(User::getEmploymentStatus, "PROBATION")
                    .set(User::getDepartmentId, change.getTargetDepartmentId())
                    .set(User::getPositionId, change.getTargetPositionId())
                    .set(User::getApproverUserId, change.getTargetSupervisorUserId());
            case "REGULARIZATION" -> update.set(User::getEmploymentStatus, "ACTIVE");
            case "TRANSFER" -> update
                    .set(User::getDepartmentId, change.getTargetDepartmentId())
                    .set(User::getPositionId, change.getTargetPositionId())
                    .set(User::getApproverUserId, change.getTargetSupervisorUserId());
            case "OFFBOARDING" -> update
                    .set(User::getStatus, 0)
                    .set(User::getEmploymentStatus, "OFFBOARDED");
            default -> throw invalid("validation.hr.change.typeInvalid");
        }
        if (userMapper.update(null, update) != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

    private void updateDecision(ResolvedUserAccess actor, EmployeeChange change, Integer version,
                                String status, String comment, LocalDateTime now, boolean applied) {
        int updated = changeMapper.update(null, new LambdaUpdateWrapper<EmployeeChange>()
                .eq(EmployeeChange::getId, change.getId())
                .eq(EmployeeChange::getTenantId, actor.tenantId())
                .eq(EmployeeChange::getReviewApproverUserId, actor.userId())
                .eq(EmployeeChange::getStatus, "PENDING")
                .eq(EmployeeChange::getVersion, version)
                .set(EmployeeChange::getStatus, status)
                .set(EmployeeChange::getDecisionComment, normalize(comment))
                .set(EmployeeChange::getDecidedAt, now)
                .set(EmployeeChange::getAppliedAt, applied ? now : null)
                .set(EmployeeChange::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (updated != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }

    /** 审批通过但尚未到生效日的变动，由服务端定时落到员工档案。 */
    @Scheduled(cron = "${app.hr.employee-change-apply-cron:0 5 0 * * *}")
    @Transactional
    public void applyDueChanges() {
        LocalDateTime now = LocalDateTime.now();
        for (EmployeeChange change : changeMapper.selectDueForUpdate(LocalDate.now())) {
            User employee = requireTenantUser(change.getTenantId(), change.getEmployeeUserId(), false);
            applyEmployeeState(change.getTenantId(), employee, change);
            int updated = changeMapper.update(null, new LambdaUpdateWrapper<EmployeeChange>()
                    .eq(EmployeeChange::getId, change.getId())
                    .eq(EmployeeChange::getTenantId, change.getTenantId())
                    .eq(EmployeeChange::getStatus, "APPROVED")
                    .isNull(EmployeeChange::getAppliedAt)
                    .set(EmployeeChange::getStatus, "EFFECTIVE")
                    .set(EmployeeChange::getAppliedAt, now)
                    .set(EmployeeChange::getUpdatedAt, now)
                    .setSql("version = version + 1"));
            if (updated != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
            auditService.recordTransactional(change.getTenantId(), change.getReviewApproverUserId(),
                    RESOURCE_TYPE, change.getId().toString(), "APPLY", "SUCCESS",
                    "员工变动到达生效日并更新员工状态");
        }
    }

    private User requireTenantUser(Long tenantId, Long userId, boolean active) {
        User user = userMapper.selectById(userId);
        if (user == null || !tenantId.equals(user.getTenantId())
                || (active && !Integer.valueOf(1).equals(user.getStatus()))) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return user;
    }

    private void notifyApplicant(ResolvedUserAccess actor, EmployeeChange change, String title) {
        notificationService.publish(actor.tenantId(), change.getApplicantUserId(),
                NotificationService.TYPE_APPROVAL, title,
                "入转调离申请状态已更新", "employee-change", change.getId());
    }

    private EmployeeChangeView requireView(Long tenantId, Long id) {
        EmployeeChangeView view = changeMapper.selectView(tenantId, id);
        if (view == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return view;
    }

    private EmployeeChangeResponse response(ResolvedUserAccess actor, EmployeeChangeView view) {
        boolean pending = "PENDING".equals(view.status());
        return new EmployeeChangeResponse(
                view.id(), view.employeeUserId(), view.employeeName(), view.employeeEmail(),
                view.applicantUserId(), view.applicantName(), view.reviewApproverUserId(),
                view.reviewApproverName(), view.changeType(), view.effectiveDate(),
                view.currentDepartmentId(), view.currentDepartmentName(), view.currentPositionId(),
                view.currentPositionName(), view.currentSupervisorUserId(), view.currentSupervisorName(),
                view.targetDepartmentId(), view.targetDepartmentName(), view.targetPositionId(),
                view.targetPositionName(), view.targetSupervisorUserId(), view.targetSupervisorName(),
                view.reason(), view.status(), view.decisionComment(), view.version(),
                pending && actor.userId().equals(view.reviewApproverUserId())
                        && actor.permissions().contains("hr:manage"),
                pending && actor.userId().equals(view.applicantUserId())
                        && actor.permissions().contains("hr:manage"),
                view.submittedAt(), view.decidedAt(), view.withdrawnAt(), view.appliedAt(),
                view.createdAt(), view.updatedAt());
    }

    private ResolvedUserAccess requirePermission(Long userId, String permission) {
        ResolvedUserAccess actor = userAccessService.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        if (!actor.permissions().contains(permission)) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        return actor;
    }

    private BusinessException invalid(String key) {
        return new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, key);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
