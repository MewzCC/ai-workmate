package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.WorkbenchRecordRequest;
import com.aiworkmate.dto.WorkbenchRecordResponse;
import com.aiworkmate.dto.WorkbenchPageResponse;
import com.aiworkmate.entity.WorkbenchRecord;
import com.aiworkmate.mapper.WorkbenchRecordMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.WorkbenchRecordService;
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
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkbenchRecordServiceImpl implements WorkbenchRecordService {
    private static final Map<String, String> MODULE_MANAGE_PERMISSIONS = Map.ofEntries(
            Map.entry("expense", "workbench:finance:manage"),
            Map.entry("budget", "workbench:finance:manage"),
            Map.entry("contracts", "workbench:finance:manage"),
            Map.entry("suppliers", "workbench:finance:manage"),
            Map.entry("api-center", "workbench:integration:manage"),
            Map.entry("page-actions", "workbench:integration:manage"),
            Map.entry("runtime-logs", "workbench:integration:manage"),
            Map.entry("sandbox-replay", "workbench:integration:manage"),
            Map.entry("data-permission", "workbench:settings:manage"),
            Map.entry("ai-permission", "workbench:settings:manage"),
            Map.entry("dictionary", "workbench:settings:manage")
    );

    private final WorkbenchRecordMapper mapper;
    private final UserAccessService userAccessService;
    private final BusinessAuditService auditService;

    @Override
    public WorkbenchPageResponse list(Long userId, String moduleKey, String keyword,
                                      String status, int page, int size) {
        String module = requireModule(moduleKey);
        ResolvedUserAccess actor = requireRead(userId, module);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<WorkbenchRecord> query = new LambdaQueryWrapper<WorkbenchRecord>()
                .eq(WorkbenchRecord::getTenantId, actor.tenantId())
                .eq(WorkbenchRecord::getModuleKey, module)
                .eq(WorkbenchRecord::getDeleted, false)
                .eq(StringUtils.hasText(status), WorkbenchRecord::getStatus, normalizeStatus(status))
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(WorkbenchRecord::getRecordCode, keyword.trim())
                        .or().like(WorkbenchRecord::getTitle, keyword.trim())
                        .or().like(WorkbenchRecord::getCategory, keyword.trim())
                        .or().like(WorkbenchRecord::getOwner, keyword.trim()))
                .orderByDesc(WorkbenchRecord::getUpdatedAt);
        Page<WorkbenchRecord> result = mapper.selectPage(new Page<>(safePage, safeSize), query);
        boolean canManage = canManage(actor, module);
        return new WorkbenchPageResponse(
                result.getRecords().stream().map(item -> response(item, canManage)).toList(),
                result.getTotal(), safePage, safeSize, canManage);
    }

    @Override
    @Transactional
    public WorkbenchRecordResponse create(Long userId, String moduleKey, WorkbenchRecordRequest request) {
        String module = requireModule(moduleKey);
        ResolvedUserAccess actor = requireManage(userId, module);
        WorkbenchRecord record = new WorkbenchRecord();
        apply(record, request);
        record.setTenantId(actor.tenantId());
        record.setModuleKey(module);
        record.setVersion(0);
        record.setDeleted(false);
        record.setCreatedBy(actor.userId());
        record.setUpdatedBy(actor.userId());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        try {
            mapper.insert(record);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.workbench.code.duplicate");
        }
        auditService.recordTransactional(actor.tenantId(), actor.userId(), "WORKBENCH_RECORD",
                String.valueOf(record.getId()), "CREATE_" + module.toUpperCase().replace('-', '_'),
                "SUCCESS", record.getRecordCode());
        return response(record, true);
    }

    @Override
    @Transactional
    public WorkbenchRecordResponse update(Long userId, String moduleKey, Long id, WorkbenchRecordRequest request) {
        String module = requireModule(moduleKey);
        ResolvedUserAccess actor = requireManage(userId, module);
        WorkbenchRecord existing = requireRecord(actor, module, id);
        if (request.version() == null || !request.version().equals(existing.getVersion())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        WorkbenchRecord changed = new WorkbenchRecord();
        apply(changed, request);
        changed.setUpdatedBy(actor.userId());
        changed.setUpdatedAt(LocalDateTime.now());
        changed.setVersion(existing.getVersion() + 1);
        int updated;
        try {
            updated = mapper.update(changed, new LambdaUpdateWrapper<WorkbenchRecord>()
                    .eq(WorkbenchRecord::getId, id)
                    .eq(WorkbenchRecord::getTenantId, actor.tenantId())
                    .eq(WorkbenchRecord::getModuleKey, module)
                    .eq(WorkbenchRecord::getDeleted, false)
                    .eq(WorkbenchRecord::getVersion, existing.getVersion()));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.workbench.code.duplicate");
        }
        if (updated != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        auditService.recordTransactional(actor.tenantId(), actor.userId(), "WORKBENCH_RECORD",
                String.valueOf(id), "UPDATE_" + module.toUpperCase().replace('-', '_'),
                "SUCCESS", changed.getRecordCode());
        return response(requireRecord(actor, module, id), true);
    }

    @Override
    @Transactional
    public void delete(Long userId, String moduleKey, Long id, Integer version) {
        String module = requireModule(moduleKey);
        ResolvedUserAccess actor = requireManage(userId, module);
        WorkbenchRecord existing = requireRecord(actor, module, id);
        if (version == null || !version.equals(existing.getVersion())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        int updated = mapper.update(null, new LambdaUpdateWrapper<WorkbenchRecord>()
                .eq(WorkbenchRecord::getId, id)
                .eq(WorkbenchRecord::getTenantId, actor.tenantId())
                .eq(WorkbenchRecord::getModuleKey, module)
                .eq(WorkbenchRecord::getVersion, version)
                .set(WorkbenchRecord::getDeleted, true)
                .set(WorkbenchRecord::getUpdatedBy, actor.userId())
                .set(WorkbenchRecord::getUpdatedAt, LocalDateTime.now())
                .set(WorkbenchRecord::getVersion, version + 1));
        if (updated != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        auditService.recordTransactional(actor.tenantId(), actor.userId(), "WORKBENCH_RECORD",
                String.valueOf(id), "DELETE_" + module.toUpperCase().replace('-', '_'),
                "SUCCESS", existing.getRecordCode());
    }

    private String requireModule(String moduleKey) {
        String module = normalize(moduleKey);
        if (!MODULE_MANAGE_PERMISSIONS.containsKey(module)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return module;
    }

    private ResolvedUserAccess requireRead(Long userId, String module) {
        ResolvedUserAccess actor = requireAccess(userId);
        if (!actor.permissions().contains("route:" + module)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return actor;
    }

    private ResolvedUserAccess requireManage(Long userId, String module) {
        ResolvedUserAccess actor = requireRead(userId, module);
        if (!canManage(actor, module)) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        return actor;
    }

    private boolean canManage(ResolvedUserAccess actor, String module) {
        return actor.permissions().contains(MODULE_MANAGE_PERMISSIONS.get(module));
    }

    private ResolvedUserAccess requireAccess(Long userId) {
        ResolvedUserAccess actor = userAccessService.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        return actor;
    }

    private WorkbenchRecord requireRecord(ResolvedUserAccess actor, String module, Long id) {
        WorkbenchRecord record = mapper.selectOne(new LambdaQueryWrapper<WorkbenchRecord>()
                .eq(WorkbenchRecord::getId, id)
                .eq(WorkbenchRecord::getTenantId, actor.tenantId())
                .eq(WorkbenchRecord::getModuleKey, module)
                .eq(WorkbenchRecord::getDeleted, false));
        if (record == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return record;
    }

    private void apply(WorkbenchRecord record, WorkbenchRecordRequest request) {
        record.setRecordCode(request.code().trim());
        record.setTitle(request.title().trim());
        record.setCategory(trimToNull(request.category()));
        record.setStatus(StringUtils.hasText(request.status()) ? normalizeStatus(request.status()) : "DRAFT");
        record.setAmount(request.amount());
        record.setOwner(trimToNull(request.owner()));
        record.setDetails(trimToNull(request.details()));
    }

    private WorkbenchRecordResponse response(WorkbenchRecord record, boolean canManage) {
        return new WorkbenchRecordResponse(record.getId(), record.getModuleKey(), record.getRecordCode(),
                record.getTitle(), record.getCategory(), record.getStatus(), record.getAmount(),
                record.getOwner(), record.getDetails(), record.getVersion(), record.getCreatedAt(),
                record.getUpdatedAt(), canManage);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
