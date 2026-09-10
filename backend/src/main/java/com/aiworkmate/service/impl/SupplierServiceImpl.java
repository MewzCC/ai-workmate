package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.SupplierDetailResponse;
import com.aiworkmate.dto.SupplierPageResponse;
import com.aiworkmate.dto.SupplierRequest;
import com.aiworkmate.dto.SupplierResponse;
import com.aiworkmate.dto.SupplierStatsResponse;
import com.aiworkmate.dto.SupplierStatusHistoryResponse;
import com.aiworkmate.dto.SupplierStatusRequest;
import com.aiworkmate.entity.Supplier;
import com.aiworkmate.entity.SupplierStatusHistory;
import com.aiworkmate.mapper.SupplierMapper;
import com.aiworkmate.mapper.SupplierStatusHistoryMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.SupplierService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {
    private static final String READ_PERMISSION = "route:suppliers";
    private static final String MANAGE_PERMISSION = "supplier:manage";
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "SUSPENDED", "BLACKLISTED");
    private static final Set<String> CATEGORIES = Set.of("MATERIAL", "SERVICE", "LOGISTICS", "CONSULTING", "OTHER");
    private static final Map<String, List<String>> TRANSITIONS = Map.of(
            "DRAFT", List.of("ACTIVE", "BLACKLISTED"),
            "ACTIVE", List.of("SUSPENDED", "BLACKLISTED"),
            "SUSPENDED", List.of("ACTIVE", "BLACKLISTED"),
            "BLACKLISTED", List.of("SUSPENDED")
    );

    private final SupplierMapper supplierMapper;
    private final SupplierStatusHistoryMapper historyMapper;
    private final UserAccessService userAccessService;
    private final BusinessAuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public SupplierPageResponse list(Long userId, String keyword, String status, String category,
                                     int page, int size) {
        ResolvedUserAccess actor = requireRead(userId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String normalizedStatus = normalizeOptional(status, STATUSES, "validation.supplier.status.invalid");
        String normalizedCategory = normalizeOptional(category, CATEGORIES, "validation.supplier.category.invalid");
        LambdaQueryWrapper<Supplier> query = baseQuery(actor.tenantId())
                .eq(StringUtils.hasText(normalizedStatus), Supplier::getStatus, normalizedStatus)
                .eq(StringUtils.hasText(normalizedCategory), Supplier::getCategory, normalizedCategory)
                .and(StringUtils.hasText(keyword), item -> item
                        .like(Supplier::getSupplierCode, keyword.trim())
                        .or().like(Supplier::getName, keyword.trim())
                        .or().like(Supplier::getShortName, keyword.trim())
                        .or().like(Supplier::getUnifiedSocialCreditCode, keyword.trim())
                        .or().like(Supplier::getContactName, keyword.trim()))
                .orderByDesc(Supplier::getUpdatedAt);
        Page<Supplier> result = supplierMapper.selectPage(new Page<>(safePage, safeSize), query);
        boolean canManage = canManage(actor);
        SupplierStatsResponse stats = new SupplierStatsResponse(
                count(actor.tenantId(), null),
                count(actor.tenantId(), "ACTIVE"),
                count(actor.tenantId(), "SUSPENDED"),
                count(actor.tenantId(), "BLACKLISTED"));
        return new SupplierPageResponse(result.getRecords().stream()
                .map(item -> response(item, canManage)).toList(), result.getTotal(), safePage, safeSize, stats, canManage);
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierDetailResponse detail(Long userId, Long id) {
        ResolvedUserAccess actor = requireRead(userId);
        Supplier supplier = requireSupplier(actor, id);
        List<SupplierStatusHistoryResponse> history = historyMapper.selectList(
                        new LambdaQueryWrapper<SupplierStatusHistory>()
                                .eq(SupplierStatusHistory::getTenantId, actor.tenantId())
                                .eq(SupplierStatusHistory::getSupplierId, id)
                                .orderByDesc(SupplierStatusHistory::getCreatedAt)
                                .orderByDesc(SupplierStatusHistory::getId))
                .stream().map(this::historyResponse).toList();
        return new SupplierDetailResponse(response(supplier, canManage(actor)), history);
    }

    @Override
    @Transactional
    public SupplierResponse create(Long userId, SupplierRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        Supplier supplier = new Supplier();
        supplier.setTenantId(actor.tenantId());
        supplier.setSupplierCode(normalizeCode(request.code()));
        apply(supplier, request);
        supplier.setStatus("DRAFT");
        LocalDateTime now = LocalDateTime.now();
        supplier.setVersion(0);
        supplier.setDeleted(false);
        supplier.setCreatedBy(actor.userId());
        supplier.setUpdatedBy(actor.userId());
        supplier.setCreatedAt(now);
        supplier.setUpdatedAt(now);
        try {
            supplierMapper.insert(supplier);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.supplier.duplicate");
        }
        insertHistory(actor, supplier.getId(), null, "DRAFT", null);
        audit(actor, supplier.getId(), "CREATE", supplier.getSupplierCode());
        return response(supplier, true);
    }

    @Override
    @Transactional
    public SupplierResponse update(Long userId, Long id, SupplierRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        Supplier existing = requireSupplier(actor, id);
        requireVersion(request.version(), existing.getVersion());
        if (!existing.getSupplierCode().equals(normalizeCode(request.code()))) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.supplier.code.immutable");
        }
        Supplier changed = new Supplier();
        apply(changed, request);
        changed.setUpdatedBy(actor.userId());
        changed.setUpdatedAt(LocalDateTime.now());
        changed.setVersion(existing.getVersion() + 1);
        int updated;
        try {
            updated = supplierMapper.update(changed, versionUpdate(actor, existing));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.supplier.duplicate");
        }
        if (updated != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        audit(actor, id, "UPDATE", existing.getSupplierCode());
        return response(requireSupplier(actor, id), true);
    }

    @Override
    @Transactional
    public SupplierResponse updateStatus(Long userId, Long id, SupplierStatusRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        Supplier existing = requireSupplier(actor, id);
        requireVersion(request.version(), existing.getVersion());
        String target = normalizeOptional(request.status(), STATUSES, "validation.supplier.status.invalid");
        if (target == null || !TRANSITIONS.getOrDefault(existing.getStatus(), List.of()).contains(target)) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.supplier.transition.invalid");
        }
        String reason = trimToNull(request.reason());
        if (("SUSPENDED".equals(target) || "BLACKLISTED".equals(target)) && reason == null) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.supplier.statusReason.required");
        }
        int updated = supplierMapper.update(null, new LambdaUpdateWrapper<Supplier>()
                .eq(Supplier::getId, id)
                .eq(Supplier::getTenantId, actor.tenantId())
                .eq(Supplier::getDeleted, false)
                .eq(Supplier::getVersion, existing.getVersion())
                .set(Supplier::getStatus, target)
                .set(Supplier::getUpdatedBy, actor.userId())
                .set(Supplier::getUpdatedAt, LocalDateTime.now())
                .set(Supplier::getVersion, existing.getVersion() + 1));
        if (updated != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        insertHistory(actor, id, existing.getStatus(), target, reason);
        audit(actor, id, "SET_STATUS", existing.getSupplierCode() + ":" + existing.getStatus() + "->" + target);
        return response(requireSupplier(actor, id), true);
    }

    private void apply(Supplier supplier, SupplierRequest request) {
        supplier.setName(request.name().trim());
        supplier.setShortName(trimToNull(request.shortName()));
        supplier.setUnifiedSocialCreditCode(upperToNull(request.unifiedSocialCreditCode()));
        supplier.setCategory(request.category().trim().toUpperCase(Locale.ROOT));
        supplier.setSupplierLevel(request.supplierLevel().trim().toUpperCase(Locale.ROOT));
        supplier.setContactName(trimToNull(request.contactName()));
        supplier.setContactPhone(trimToNull(request.contactPhone()));
        supplier.setContactEmail(lowerToNull(request.contactEmail()));
        supplier.setAddress(trimToNull(request.address()));
        supplier.setPaymentTerms(trimToNull(request.paymentTerms()));
        supplier.setRiskNote(trimToNull(request.riskNote()));
    }

    private LambdaUpdateWrapper<Supplier> versionUpdate(ResolvedUserAccess actor, Supplier existing) {
        return new LambdaUpdateWrapper<Supplier>()
                .eq(Supplier::getId, existing.getId())
                .eq(Supplier::getTenantId, actor.tenantId())
                .eq(Supplier::getDeleted, false)
                .eq(Supplier::getVersion, existing.getVersion());
    }

    private void insertHistory(ResolvedUserAccess actor, Long supplierId, String fromStatus,
                               String toStatus, String reason) {
        SupplierStatusHistory history = new SupplierStatusHistory();
        history.setTenantId(actor.tenantId());
        history.setSupplierId(supplierId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setReason(reason);
        history.setOperatorId(actor.userId());
        history.setOperatorLabel(actor.username());
        history.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(history);
    }

    private SupplierResponse response(Supplier item, boolean canManage) {
        return new SupplierResponse(item.getId(), item.getSupplierCode(), item.getName(), item.getShortName(),
                item.getUnifiedSocialCreditCode(), item.getCategory(), item.getSupplierLevel(), item.getStatus(),
                item.getContactName(), item.getContactPhone(), item.getContactEmail(), item.getAddress(),
                item.getPaymentTerms(), item.getRiskNote(), item.getVersion(), item.getCreatedAt(), item.getUpdatedAt(),
                canManage, canManage ? TRANSITIONS.getOrDefault(item.getStatus(), List.of()) : List.of());
    }

    private SupplierStatusHistoryResponse historyResponse(SupplierStatusHistory item) {
        return new SupplierStatusHistoryResponse(item.getId(), item.getFromStatus(), item.getToStatus(),
                item.getReason(), item.getOperatorLabel(), item.getCreatedAt());
    }

    private long count(Long tenantId, String status) {
        return supplierMapper.selectCount(baseQuery(tenantId).eq(status != null, Supplier::getStatus, status));
    }

    private LambdaQueryWrapper<Supplier> baseQuery(Long tenantId) {
        return new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getTenantId, tenantId)
                .eq(Supplier::getDeleted, false);
    }

    private Supplier requireSupplier(ResolvedUserAccess actor, Long id) {
        Supplier supplier = supplierMapper.selectOne(baseQuery(actor.tenantId()).eq(Supplier::getId, id));
        if (supplier == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return supplier;
    }

    private ResolvedUserAccess requireRead(Long userId) {
        ResolvedUserAccess actor = requireAccess(userId);
        if (!actor.permissions().contains(READ_PERMISSION)) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        return actor;
    }

    private ResolvedUserAccess requireManage(Long userId) {
        ResolvedUserAccess actor = requireRead(userId);
        if (!canManage(actor)) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        return actor;
    }

    private ResolvedUserAccess requireAccess(Long userId) {
        ResolvedUserAccess actor = userAccessService.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        return actor;
    }

    private boolean canManage(ResolvedUserAccess actor) {
        return actor.permissions().contains(MANAGE_PERMISSION);
    }

    private String normalizeOptional(String value, Set<String> allowed, String messageKey) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new BusinessException(ErrorCode.REQUEST_INVALID, messageKey);
        return normalized;
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String upperToNull(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String lowerToNull(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private void requireVersion(Integer actual, Integer expected) {
        if (actual == null || !actual.equals(expected)) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }

    private void audit(ResolvedUserAccess actor, Long id, String action, String summary) {
        auditService.recordTransactional(actor.tenantId(), actor.userId(), "SUPPLIER", String.valueOf(id),
                action, "SUCCESS", summary);
    }
}
