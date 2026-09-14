package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.DictionaryItemPageResponse;
import com.aiworkmate.dto.DictionaryItemRequest;
import com.aiworkmate.dto.DictionaryItemResponse;
import com.aiworkmate.dto.DictionaryOptionResponse;
import com.aiworkmate.dto.DictionaryStatusRequest;
import com.aiworkmate.dto.DictionaryTypeListResponse;
import com.aiworkmate.dto.DictionaryTypeRequest;
import com.aiworkmate.dto.DictionaryTypeResponse;
import com.aiworkmate.entity.DataDictionaryItem;
import com.aiworkmate.entity.DataDictionaryItemUsage;
import com.aiworkmate.entity.DataDictionaryType;
import com.aiworkmate.mapper.DataDictionaryItemMapper;
import com.aiworkmate.mapper.DataDictionaryItemUsageMapper;
import com.aiworkmate.mapper.DataDictionaryTypeMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.DataDictionaryService;
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

@Service
@RequiredArgsConstructor
public class DataDictionaryServiceImpl implements DataDictionaryService {
    private static final String READ_PERMISSION = "route:dictionary";
    private static final String MANAGE_PERMISSION = "dictionary:manage";

    private final DataDictionaryTypeMapper typeMapper;
    private final DataDictionaryItemMapper itemMapper;
    private final DataDictionaryItemUsageMapper usageMapper;
    private final UserAccessService userAccessService;
    private final BusinessAuditService auditService;

    @Override
    public DictionaryTypeListResponse listTypes(Long userId, String keyword, String status) {
        ResolvedUserAccess actor = requireRead(userId);
        String normalizedStatus = normalizeOptionalStatus(status);
        List<DataDictionaryType> types = typeMapper.selectList(new LambdaQueryWrapper<DataDictionaryType>()
                .eq(DataDictionaryType::getTenantId, actor.tenantId())
                .eq(DataDictionaryType::getDeleted, false)
                .eq(StringUtils.hasText(normalizedStatus), DataDictionaryType::getStatus, normalizedStatus)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(DataDictionaryType::getCode, keyword.trim())
                        .or().like(DataDictionaryType::getName, keyword.trim()))
                .orderByAsc(DataDictionaryType::getSortOrder)
                .orderByAsc(DataDictionaryType::getCode));
        boolean canManage = canManage(actor);
        return new DictionaryTypeListResponse(types.stream().map(type -> typeResponse(actor, type, canManage)).toList(), canManage);
    }

    @Override
    @Transactional
    public DictionaryTypeResponse createType(Long userId, DictionaryTypeRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        DataDictionaryType type = new DataDictionaryType();
        type.setTenantId(actor.tenantId());
        type.setCode(request.code().trim().toUpperCase());
        applyType(type, request);
        type.setStatus("ACTIVE");
        initialize(type, actor.userId());
        try {
            typeMapper.insert(type);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.dictionary.code.duplicate");
        }
        audit(actor, "DICTIONARY_TYPE", type.getId(), "CREATE", type.getCode());
        return typeResponse(actor, type, true);
    }

    @Override
    @Transactional
    public DictionaryTypeResponse updateType(Long userId, Long id, DictionaryTypeRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        DataDictionaryType existing = requireType(actor, id);
        requireVersion(request.version(), existing.getVersion());
        if (!existing.getCode().equals(request.code().trim().toUpperCase())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.dictionary.code.immutable");
        }
        DataDictionaryType changed = new DataDictionaryType();
        applyType(changed, request);
        updateTypeWithVersion(actor, existing, changed);
        audit(actor, "DICTIONARY_TYPE", id, "UPDATE", existing.getCode());
        return typeResponse(actor, requireType(actor, id), true);
    }

    @Override
    @Transactional
    public DictionaryTypeResponse updateTypeStatus(Long userId, Long id, DictionaryStatusRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        DataDictionaryType existing = requireType(actor, id);
        requireVersion(request.version(), existing.getVersion());
        DataDictionaryType changed = new DataDictionaryType();
        changed.setStatus(request.status());
        updateTypeWithVersion(actor, existing, changed);
        audit(actor, "DICTIONARY_TYPE", id, "SET_STATUS", existing.getCode() + ":" + request.status());
        return typeResponse(actor, requireType(actor, id), true);
    }

    @Override
    @Transactional
    public void deleteType(Long userId, Long id, Integer version) {
        ResolvedUserAccess actor = requireManage(userId);
        DataDictionaryType existing = requireType(actor, id);
        requireVersion(version, existing.getVersion());
        long itemCount = itemMapper.selectCount(itemQuery(actor.tenantId(), id));
        if (itemCount > 0) throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.dictionary.type.notEmpty");
        int updated = typeMapper.update(null, new LambdaUpdateWrapper<DataDictionaryType>()
                .eq(DataDictionaryType::getId, id).eq(DataDictionaryType::getTenantId, actor.tenantId())
                .eq(DataDictionaryType::getDeleted, false).eq(DataDictionaryType::getVersion, version)
                .set(DataDictionaryType::getDeleted, true).set(DataDictionaryType::getUpdatedBy, actor.userId())
                .set(DataDictionaryType::getUpdatedAt, LocalDateTime.now()).set(DataDictionaryType::getVersion, version + 1));
        if (updated != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        audit(actor, "DICTIONARY_TYPE", id, "DELETE", existing.getCode());
    }

    @Override
    public DictionaryItemPageResponse listItems(Long userId, Long typeId, String keyword, String status, int page, int size) {
        ResolvedUserAccess actor = requireRead(userId);
        requireType(actor, typeId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String normalizedStatus = normalizeOptionalStatus(status);
        LambdaQueryWrapper<DataDictionaryItem> query = itemQuery(actor.tenantId(), typeId)
                .eq(StringUtils.hasText(normalizedStatus), DataDictionaryItem::getStatus, normalizedStatus)
                .and(StringUtils.hasText(keyword), item -> item.like(DataDictionaryItem::getValue, keyword.trim())
                        .or().like(DataDictionaryItem::getLabel, keyword.trim()))
                .orderByAsc(DataDictionaryItem::getSortOrder).orderByAsc(DataDictionaryItem::getId);
        Page<DataDictionaryItem> result = itemMapper.selectPage(new Page<>(safePage, safeSize), query);
        boolean canManage = canManage(actor);
        return new DictionaryItemPageResponse(result.getRecords().stream()
                .map(item -> itemResponse(actor, item, canManage)).toList(), result.getTotal(), safePage, safeSize, canManage);
    }

    @Override
    @Transactional
    public DictionaryItemResponse createItem(Long userId, Long typeId, DictionaryItemRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        requireType(actor, typeId);
        DataDictionaryItem item = new DataDictionaryItem();
        item.setTenantId(actor.tenantId());
        item.setDictionaryTypeId(typeId);
        item.setValue(request.value().trim());
        applyItem(item, request);
        item.setStatus("ACTIVE");
        initialize(item, actor.userId());
        try {
            itemMapper.insert(item);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.dictionary.value.duplicate");
        }
        audit(actor, "DICTIONARY_ITEM", item.getId(), "CREATE", item.getValue());
        return itemResponse(actor, item, true);
    }

    @Override
    @Transactional
    public DictionaryItemResponse updateItem(Long userId, Long typeId, Long itemId, DictionaryItemRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        DataDictionaryItem existing = requireItem(actor, typeId, itemId);
        requireVersion(request.version(), existing.getVersion());
        if (!existing.getValue().equals(request.value().trim())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.dictionary.value.immutable");
        }
        DataDictionaryItem changed = new DataDictionaryItem();
        applyItem(changed, request);
        updateItemWithVersion(actor, existing, changed);
        audit(actor, "DICTIONARY_ITEM", itemId, "UPDATE", existing.getValue());
        return itemResponse(actor, requireItem(actor, typeId, itemId), true);
    }

    @Override
    @Transactional
    public DictionaryItemResponse updateItemStatus(Long userId, Long typeId, Long itemId, DictionaryStatusRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        DataDictionaryItem existing = requireItem(actor, typeId, itemId);
        requireVersion(request.version(), existing.getVersion());
        DataDictionaryItem changed = new DataDictionaryItem();
        changed.setStatus(request.status());
        updateItemWithVersion(actor, existing, changed);
        audit(actor, "DICTIONARY_ITEM", itemId, "SET_STATUS", existing.getValue() + ":" + request.status());
        return itemResponse(actor, requireItem(actor, typeId, itemId), true);
    }

    @Override
    @Transactional
    public void deleteItem(Long userId, Long typeId, Long itemId, Integer version) {
        ResolvedUserAccess actor = requireManage(userId);
        DataDictionaryItem existing = requireItem(actor, typeId, itemId);
        requireVersion(version, existing.getVersion());
        if (usageCount(actor.tenantId(), itemId) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.dictionary.item.inUse");
        }
        int updated = itemMapper.update(null, new LambdaUpdateWrapper<DataDictionaryItem>()
                .eq(DataDictionaryItem::getId, itemId).eq(DataDictionaryItem::getTenantId, actor.tenantId())
                .eq(DataDictionaryItem::getDictionaryTypeId, typeId).eq(DataDictionaryItem::getDeleted, false)
                .eq(DataDictionaryItem::getVersion, version).set(DataDictionaryItem::getDeleted, true)
                .set(DataDictionaryItem::getUpdatedBy, actor.userId()).set(DataDictionaryItem::getUpdatedAt, LocalDateTime.now())
                .set(DataDictionaryItem::getVersion, version + 1));
        if (updated != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        audit(actor, "DICTIONARY_ITEM", itemId, "DELETE", existing.getValue());
    }

    @Override
    public List<DictionaryOptionResponse> activeOptions(Long userId, String code) {
        ResolvedUserAccess actor = requireAccess(userId);
        DataDictionaryType type = typeMapper.selectOne(new LambdaQueryWrapper<DataDictionaryType>()
                .eq(DataDictionaryType::getTenantId, actor.tenantId()).eq(DataDictionaryType::getCode, code.trim().toUpperCase())
                .eq(DataDictionaryType::getStatus, "ACTIVE").eq(DataDictionaryType::getDeleted, false));
        if (type == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return itemMapper.selectList(itemQuery(actor.tenantId(), type.getId())
                        .eq(DataDictionaryItem::getStatus, "ACTIVE")
                        .orderByAsc(DataDictionaryItem::getSortOrder).orderByAsc(DataDictionaryItem::getId))
                .stream().map(item -> new DictionaryOptionResponse(item.getValue(), item.getLabel(), item.getSortOrder())).toList();
    }

    private void applyType(DataDictionaryType type, DictionaryTypeRequest request) {
        type.setName(request.name().trim());
        type.setDescription(trimToNull(request.description()));
        type.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private void applyItem(DataDictionaryItem item, DictionaryItemRequest request) {
        item.setLabel(request.label().trim());
        item.setDescription(trimToNull(request.description()));
        item.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private void initialize(DataDictionaryType type, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        type.setVersion(0); type.setDeleted(false); type.setCreatedBy(userId); type.setUpdatedBy(userId);
        type.setCreatedAt(now); type.setUpdatedAt(now);
    }

    private void initialize(DataDictionaryItem item, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        item.setVersion(0); item.setDeleted(false); item.setCreatedBy(userId); item.setUpdatedBy(userId);
        item.setCreatedAt(now); item.setUpdatedAt(now);
    }

    private void updateTypeWithVersion(ResolvedUserAccess actor, DataDictionaryType existing, DataDictionaryType changed) {
        changed.setUpdatedBy(actor.userId()); changed.setUpdatedAt(LocalDateTime.now()); changed.setVersion(existing.getVersion() + 1);
        int updated = typeMapper.update(changed, new LambdaUpdateWrapper<DataDictionaryType>()
                .eq(DataDictionaryType::getId, existing.getId()).eq(DataDictionaryType::getTenantId, actor.tenantId())
                .eq(DataDictionaryType::getDeleted, false).eq(DataDictionaryType::getVersion, existing.getVersion()));
        if (updated != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }

    private void updateItemWithVersion(ResolvedUserAccess actor, DataDictionaryItem existing, DataDictionaryItem changed) {
        changed.setUpdatedBy(actor.userId()); changed.setUpdatedAt(LocalDateTime.now()); changed.setVersion(existing.getVersion() + 1);
        int updated = itemMapper.update(changed, new LambdaUpdateWrapper<DataDictionaryItem>()
                .eq(DataDictionaryItem::getId, existing.getId()).eq(DataDictionaryItem::getTenantId, actor.tenantId())
                .eq(DataDictionaryItem::getDictionaryTypeId, existing.getDictionaryTypeId())
                .eq(DataDictionaryItem::getDeleted, false).eq(DataDictionaryItem::getVersion, existing.getVersion()));
        if (updated != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }

    private DictionaryTypeResponse typeResponse(ResolvedUserAccess actor, DataDictionaryType type, boolean canManage) {
        int itemCount = Math.toIntExact(itemMapper.selectCount(itemQuery(actor.tenantId(), type.getId())));
        int activeCount = Math.toIntExact(itemMapper.selectCount(itemQuery(actor.tenantId(), type.getId())
                .eq(DataDictionaryItem::getStatus, "ACTIVE")));
        return new DictionaryTypeResponse(type.getId(), type.getCode(), type.getName(), type.getDescription(),
                type.getStatus(), type.getSortOrder(), itemCount, activeCount, type.getVersion(), type.getUpdatedAt(), canManage);
    }

    private DictionaryItemResponse itemResponse(ResolvedUserAccess actor, DataDictionaryItem item, boolean canManage) {
        long usages = usageCount(actor.tenantId(), item.getId());
        return new DictionaryItemResponse(item.getId(), item.getDictionaryTypeId(), item.getValue(), item.getLabel(),
                item.getDescription(), item.getStatus(), item.getSortOrder(), usages, item.getVersion(), item.getUpdatedAt(),
                canManage, canManage && usages == 0);
    }

    private long usageCount(Long tenantId, Long itemId) {
        return usageMapper.selectCount(new LambdaQueryWrapper<DataDictionaryItemUsage>()
                .eq(DataDictionaryItemUsage::getTenantId, tenantId).eq(DataDictionaryItemUsage::getDictionaryItemId, itemId));
    }

    private LambdaQueryWrapper<DataDictionaryItem> itemQuery(Long tenantId, Long typeId) {
        return new LambdaQueryWrapper<DataDictionaryItem>().eq(DataDictionaryItem::getTenantId, tenantId)
                .eq(DataDictionaryItem::getDictionaryTypeId, typeId).eq(DataDictionaryItem::getDeleted, false);
    }

    private DataDictionaryType requireType(ResolvedUserAccess actor, Long id) {
        DataDictionaryType type = typeMapper.selectOne(new LambdaQueryWrapper<DataDictionaryType>()
                .eq(DataDictionaryType::getId, id).eq(DataDictionaryType::getTenantId, actor.tenantId())
                .eq(DataDictionaryType::getDeleted, false));
        if (type == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return type;
    }

    private DataDictionaryItem requireItem(ResolvedUserAccess actor, Long typeId, Long itemId) {
        DataDictionaryItem item = itemMapper.selectOne(itemQuery(actor.tenantId(), typeId).eq(DataDictionaryItem::getId, itemId));
        if (item == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return item;
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

    private boolean canManage(ResolvedUserAccess actor) { return actor.permissions().contains(MANAGE_PERMISSION); }
    private void requireVersion(Integer actual, Integer expected) { if (actual == null || !actual.equals(expected)) throw new BusinessException(ErrorCode.VERSION_CONFLICT); }
    private String normalizeOptionalStatus(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim().toUpperCase();
        if (!normalized.equals("ACTIVE") && !normalized.equals("DISABLED")) throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.dictionary.status.invalid");
        return normalized;
    }
    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private void audit(ResolvedUserAccess actor, String resourceType, Long id, String action, String summary) {
        auditService.recordTransactional(actor.tenantId(), actor.userId(), resourceType, String.valueOf(id), action, "SUCCESS", summary);
    }
}
