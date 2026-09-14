package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.TenantBusinessRequest;
import com.aiworkmate.dto.TenantConfigurationHistoryPageResponse;
import com.aiworkmate.dto.TenantConfigurationHistoryResponse;
import com.aiworkmate.dto.TenantConfigurationResponse;
import com.aiworkmate.dto.TenantFeaturesRequest;
import com.aiworkmate.dto.TenantProfileRequest;
import com.aiworkmate.dto.TenantSecurityRequest;
import com.aiworkmate.entity.TenantConfiguration;
import com.aiworkmate.entity.TenantConfigurationHistory;
import com.aiworkmate.mapper.TenantConfigurationHistoryMapper;
import com.aiworkmate.mapper.TenantConfigurationMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.TenantConfigurationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class TenantConfigurationServiceImpl implements TenantConfigurationService {
    private static final String READ_PERMISSION = "route:tenant-config";
    private static final String MANAGE_PERMISSION = "tenant:config:manage";

    private final TenantConfigurationMapper configurationMapper;
    private final TenantConfigurationHistoryMapper historyMapper;
    private final UserAccessService userAccessService;
    private final BusinessAuditService auditService;
    private final TenantConfigurationCache cache;

    @Override
    public TenantConfigurationResponse get(Long userId) {
        ResolvedUserAccess actor = requireRead(userId);
        return response(load(actor), canManage(actor));
    }

    @Override
    @Transactional
    public TenantConfigurationResponse updateProfile(Long userId, TenantProfileRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        validateTimezone(request.timezone());
        return update(actor, request.version(), "PROFILE", changed -> {
            changed.setTenantName(request.tenantName().trim());
            changed.setTenantShortName(trimToNull(request.tenantShortName()));
            changed.setLocale(request.locale());
            changed.setTimezone(request.timezone());
        });
    }

    @Override
    @Transactional
    public TenantConfigurationResponse updateFeatures(Long userId, TenantFeaturesRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        return update(actor, request.version(), "FEATURES", changed -> {
            changed.setApprovalEnabled(request.approvalEnabled());
            changed.setAttendanceEnabled(request.attendanceEnabled());
            changed.setAssetEnabled(request.assetEnabled());
            changed.setMeetingEnabled(request.meetingEnabled());
            changed.setVisitorEnabled(request.visitorEnabled());
            changed.setSealEnabled(request.sealEnabled());
        });
    }

    @Override
    @Transactional
    public TenantConfigurationResponse updateBusiness(Long userId, TenantBusinessRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        return update(actor, request.version(), "BUSINESS", changed -> {
            changed.setFiscalYearStartMonth(request.fiscalYearStartMonth());
            changed.setDefaultApprovalDays(request.defaultApprovalDays());
            changed.setExpenseCurrency(request.expenseCurrency());
        });
    }

    @Override
    @Transactional
    public TenantConfigurationResponse updateSecurity(Long userId, TenantSecurityRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        return update(actor, request.version(), "SECURITY", changed -> {
            changed.setPasswordMinLength(request.passwordMinLength());
            changed.setSessionTimeoutMinutes(request.sessionTimeoutMinutes());
        });
    }

    @Override
    public TenantConfigurationHistoryPageResponse history(Long userId, int page, int size) {
        ResolvedUserAccess actor = requireRead(userId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Page<TenantConfigurationHistory> result = historyMapper.selectPage(new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<TenantConfigurationHistory>()
                        .eq(TenantConfigurationHistory::getTenantId, actor.tenantId())
                        .orderByDesc(TenantConfigurationHistory::getCreatedAt)
                        .orderByDesc(TenantConfigurationHistory::getId));
        return new TenantConfigurationHistoryPageResponse(result.getRecords().stream()
                .map(item -> new TenantConfigurationHistoryResponse(item.getId(), item.getCategory(), item.getVersion(),
                        item.getChangedBy(), item.getCreatedAt())).toList(), result.getTotal(), safePage, safeSize);
    }

    private TenantConfigurationResponse update(ResolvedUserAccess actor, Integer version, String category,
            Consumer<TenantConfiguration> mutator) {
        TenantConfiguration current = loadFromDatabase(actor);
        if (version == null || !version.equals(current.getVersion())) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        TenantConfiguration changed = new TenantConfiguration();
        mutator.accept(changed);
        changed.setUpdatedBy(actor.userId());
        changed.setUpdatedAt(LocalDateTime.now());
        changed.setVersion(current.getVersion() + 1);
        int updated = configurationMapper.update(changed, new LambdaUpdateWrapper<TenantConfiguration>()
                .eq(TenantConfiguration::getTenantId, actor.tenantId())
                .eq(TenantConfiguration::getVersion, current.getVersion()));
        if (updated != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        TenantConfiguration saved = loadFromDatabase(actor);
        if ("PROFILE".equals(category)) configurationMapper.updateTenantName(actor.tenantId(), saved.getTenantName());
        historyMapper.insert(history(saved, category, actor.userId()));
        auditService.recordTransactional(actor.tenantId(), actor.userId(), "TENANT_CONFIGURATION",
                String.valueOf(saved.getId()), "UPDATE_" + category, "SUCCESS", "version=" + saved.getVersion());
        evictAfterCommit(actor.tenantId());
        return response(saved, true);
    }

    private TenantConfiguration load(ResolvedUserAccess actor) {
        return cache.get(actor.tenantId()).orElseGet(() -> {
            TenantConfiguration configuration = loadFromDatabase(actor);
            cache.put(actor.tenantId(), configuration);
            return configuration;
        });
    }

    private TenantConfiguration loadFromDatabase(ResolvedUserAccess actor) {
        TenantConfiguration configuration = configurationMapper.selectOne(new LambdaQueryWrapper<TenantConfiguration>()
                .eq(TenantConfiguration::getTenantId, actor.tenantId()));
        if (configuration == null) {
            configurationMapper.ensureForTenant(actor.tenantId(), actor.userId());
            configuration = configurationMapper.selectOne(new LambdaQueryWrapper<TenantConfiguration>()
                    .eq(TenantConfiguration::getTenantId, actor.tenantId()));
        }
        if (configuration == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return configuration;
    }

    private TenantConfigurationHistory history(TenantConfiguration source, String category, Long changedBy) {
        TenantConfigurationHistory history = new TenantConfigurationHistory();
        history.setTenantId(source.getTenantId()); history.setCategory(category); history.setVersion(source.getVersion());
        history.setTenantName(source.getTenantName()); history.setTenantShortName(source.getTenantShortName());
        history.setLocale(source.getLocale()); history.setTimezone(source.getTimezone());
        history.setFiscalYearStartMonth(source.getFiscalYearStartMonth());
        history.setApprovalEnabled(source.getApprovalEnabled()); history.setAttendanceEnabled(source.getAttendanceEnabled());
        history.setAssetEnabled(source.getAssetEnabled()); history.setMeetingEnabled(source.getMeetingEnabled());
        history.setVisitorEnabled(source.getVisitorEnabled()); history.setSealEnabled(source.getSealEnabled());
        history.setDefaultApprovalDays(source.getDefaultApprovalDays()); history.setExpenseCurrency(source.getExpenseCurrency());
        history.setPasswordMinLength(source.getPasswordMinLength()); history.setSessionTimeoutMinutes(source.getSessionTimeoutMinutes());
        history.setChangedBy(changedBy); history.setCreatedAt(LocalDateTime.now());
        return history;
    }

    private TenantConfigurationResponse response(TenantConfiguration value, boolean canManage) {
        return new TenantConfigurationResponse(value.getId(), value.getTenantName(), value.getTenantShortName(),
                value.getLocale(), value.getTimezone(), value.getFiscalYearStartMonth(), value.getApprovalEnabled(),
                value.getAttendanceEnabled(), value.getAssetEnabled(), value.getMeetingEnabled(), value.getVisitorEnabled(),
                value.getSealEnabled(), value.getDefaultApprovalDays(), value.getExpenseCurrency(),
                value.getPasswordMinLength(), value.getSessionTimeoutMinutes(), value.getVersion(), value.getUpdatedAt(), canManage);
    }

    private ResolvedUserAccess requireRead(Long userId) {
        ResolvedUserAccess actor = userAccessService.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        if (!actor.permissions().contains(READ_PERMISSION)) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        return actor;
    }

    private ResolvedUserAccess requireManage(Long userId) {
        ResolvedUserAccess actor = requireRead(userId);
        if (!canManage(actor)) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        return actor;
    }

    private boolean canManage(ResolvedUserAccess actor) { return actor.permissions().contains(MANAGE_PERMISSION); }
    private void evictAfterCommit(Long tenantId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cache.evict(tenantId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cache.evict(tenantId); }
        });
    }
    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private void validateTimezone(String timezone) {
        try { ZoneId.of(timezone); }
        catch (DateTimeException exception) { throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.tenant.timezone.invalid"); }
    }
}
