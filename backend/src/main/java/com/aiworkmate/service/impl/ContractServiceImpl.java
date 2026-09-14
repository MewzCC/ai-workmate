package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.ContractDetailResponse;
import com.aiworkmate.dto.ContractEventResponse;
import com.aiworkmate.dto.ContractFulfillmentRequest;
import com.aiworkmate.dto.ContractOptionResponse;
import com.aiworkmate.dto.ContractOptionsResponse;
import com.aiworkmate.dto.ContractPageResponse;
import com.aiworkmate.dto.ContractPaymentRequest;
import com.aiworkmate.dto.ContractReminderRequest;
import com.aiworkmate.dto.ContractRequest;
import com.aiworkmate.dto.ContractResponse;
import com.aiworkmate.dto.ContractStatsResponse;
import com.aiworkmate.dto.ContractStatusRequest;
import com.aiworkmate.entity.BusinessContract;
import com.aiworkmate.entity.ContractEvent;
import com.aiworkmate.entity.Supplier;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.BusinessContractMapper;
import com.aiworkmate.mapper.ContractEventMapper;
import com.aiworkmate.mapper.SupplierMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.ContractService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {
    private static final String READ_PERMISSION = "route:contracts";
    private static final String MANAGE_PERMISSION = "contract:manage";
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "COMPLETED", "TERMINATED");
    private static final Set<String> TYPES = Set.of("PURCHASE", "SALES", "SERVICE", "LEASE", "OTHER");
    private static final Set<String> FULFILLMENT_STATUSES = Set.of("NOT_STARTED", "IN_PROGRESS", "FULFILLED", "BREACHED");
    private static final Map<String, List<String>> TRANSITIONS = Map.of(
            "DRAFT", List.of("ACTIVE", "TERMINATED"),
            "ACTIVE", List.of("COMPLETED", "TERMINATED"),
            "COMPLETED", List.of(),
            "TERMINATED", List.of()
    );
    private static final Map<String, List<String>> FULFILLMENT_TRANSITIONS = Map.of(
            "NOT_STARTED", List.of("IN_PROGRESS", "FULFILLED", "BREACHED"),
            "IN_PROGRESS", List.of("FULFILLED", "BREACHED"),
            "BREACHED", List.of("IN_PROGRESS", "FULFILLED"),
            "FULFILLED", List.of()
    );
    private static final Duration REMINDER_COOLDOWN = Duration.ofHours(24);

    private final BusinessContractMapper contractMapper;
    private final ContractEventMapper eventMapper;
    private final SupplierMapper supplierMapper;
    private final UserMapper userMapper;
    private final UserAccessService userAccessService;
    private final BusinessAuditService auditService;
    private final NotificationService notificationService;
    private final MessageSource messageSource;

    @Override
    @Transactional(readOnly = true)
    public ContractPageResponse list(Long userId, String keyword, String status, String contractType,
                                     String expiryState, int page, int size) {
        ResolvedUserAccess actor = requireRead(userId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String normalizedStatus = normalizeOptional(status, STATUSES, "validation.contract.status.invalid");
        String normalizedType = normalizeOptional(contractType, TYPES, "validation.contract.type.invalid");
        String normalizedExpiry = normalizeOptional(expiryState, Set.of("EXPIRING", "EXPIRED"),
                "validation.contract.expiry.invalid");
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<BusinessContract> query = baseQuery(actor.tenantId())
                .eq(StringUtils.hasText(normalizedStatus), BusinessContract::getStatus, normalizedStatus)
                .eq(StringUtils.hasText(normalizedType), BusinessContract::getContractType, normalizedType)
                .eq(StringUtils.hasText(normalizedExpiry), BusinessContract::getStatus, "ACTIVE")
                .lt("EXPIRED".equals(normalizedExpiry), BusinessContract::getEndDate, today)
                .between("EXPIRING".equals(normalizedExpiry), BusinessContract::getEndDate, today, today.plusDays(30))
                .and(StringUtils.hasText(keyword), item -> item
                        .like(BusinessContract::getContractCode, keyword.trim())
                        .or().like(BusinessContract::getName, keyword.trim())
                        .or().like(BusinessContract::getCounterpartyName, keyword.trim())
                        .or().like(BusinessContract::getOwnerLabel, keyword.trim()))
                .orderByAsc(BusinessContract::getEndDate)
                .orderByDesc(BusinessContract::getUpdatedAt);
        Page<BusinessContract> result = contractMapper.selectPage(new Page<>(safePage, safeSize), query);
        boolean canManage = canManage(actor);
        ContractStatsResponse stats = new ContractStatsResponse(
                count(actor.tenantId(), null, null, null),
                count(actor.tenantId(), "ACTIVE", null, null),
                count(actor.tenantId(), "ACTIVE", today, today.plusDays(30)),
                count(actor.tenantId(), "ACTIVE", null, today.minusDays(1)),
                defaultAmount(contractMapper.selectOutstandingAmount(actor.tenantId())));
        return new ContractPageResponse(result.getRecords().stream().map(item -> response(item, canManage)).toList(),
                result.getTotal(), safePage, safeSize, stats, canManage);
    }

    @Override
    @Transactional(readOnly = true)
    public ContractDetailResponse detail(Long userId, Long id) {
        ResolvedUserAccess actor = requireRead(userId);
        BusinessContract contract = requireContract(actor, id);
        List<ContractEventResponse> events = eventMapper.selectList(new LambdaQueryWrapper<ContractEvent>()
                        .eq(ContractEvent::getTenantId, actor.tenantId())
                        .eq(ContractEvent::getContractId, id)
                        .orderByDesc(ContractEvent::getCreatedAt).orderByDesc(ContractEvent::getId))
                .stream().map(this::eventResponse).toList();
        return new ContractDetailResponse(response(contract, canManage(actor)), events);
    }

    @Override
    @Transactional(readOnly = true)
    public ContractOptionsResponse options(Long userId) {
        ResolvedUserAccess actor = requireRead(userId);
        List<ContractOptionResponse> owners = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .eq(User::getTenantId, actor.tenantId()).eq(User::getStatus, 1)
                        .orderByAsc(User::getDisplayName).orderByAsc(User::getUsername))
                .stream().map(user -> new ContractOptionResponse(user.getId(), userLabel(user), user.getEmail())).toList();
        List<ContractOptionResponse> suppliers = supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                        .eq(Supplier::getTenantId, actor.tenantId()).eq(Supplier::getDeleted, false)
                        .eq(Supplier::getStatus, "ACTIVE").orderByAsc(Supplier::getName))
                .stream().map(item -> new ContractOptionResponse(item.getId(), item.getName(), item.getSupplierCode())).toList();
        return new ContractOptionsResponse(owners, suppliers);
    }

    @Override
    @Transactional
    public ContractResponse create(Long userId, ContractRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        validateRequest(request);
        User owner = requireOwner(actor, request.ownerUserId());
        Supplier supplier = requireSupplier(actor, request.supplierId());
        BusinessContract contract = new BusinessContract();
        contract.setTenantId(actor.tenantId());
        contract.setContractCode(normalizeCode(request.code()));
        apply(contract, request, owner, supplier);
        contract.setPaidAmount(BigDecimal.ZERO);
        contract.setStatus("DRAFT");
        contract.setFulfillmentStatus("NOT_STARTED");
        contract.setReminderCount(0);
        contract.setVersion(0);
        contract.setDeleted(false);
        contract.setCreatedBy(actor.userId());
        contract.setUpdatedBy(actor.userId());
        LocalDateTime now = LocalDateTime.now();
        contract.setCreatedAt(now);
        contract.setUpdatedAt(now);
        try {
            contractMapper.insert(contract);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.contract.duplicate");
        }
        event(actor, contract.getId(), "CREATED", null, "DRAFT", null, null);
        audit(actor, contract.getId(), "CREATE", contract.getContractCode());
        return response(contract, true);
    }

    @Override
    @Transactional
    public ContractResponse update(Long userId, Long id, ContractRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        BusinessContract existing = requireContract(actor, id);
        requireVersion(request.version(), existing.getVersion());
        if (!"DRAFT".equals(existing.getStatus()) && !"ACTIVE".equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.contract.edit.closed");
        }
        if (!existing.getContractCode().equals(normalizeCode(request.code()))) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.contract.code.immutable");
        }
        validateRequest(request);
        if (request.amount().compareTo(existing.getPaidAmount()) < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.contract.amount.belowPaid");
        }
        User owner = requireOwner(actor, request.ownerUserId());
        Supplier supplier = requireSupplier(actor, request.supplierId());
        BusinessContract changed = new BusinessContract();
        apply(changed, request, owner, supplier);
        changed.setUpdatedBy(actor.userId());
        changed.setUpdatedAt(LocalDateTime.now());
        changed.setVersion(existing.getVersion() + 1);
        int updated;
        try {
            updated = contractMapper.update(changed, versionUpdate(actor, existing));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.contract.duplicate");
        }
        if (updated != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        event(actor, id, "UPDATED", null, null, null, null);
        audit(actor, id, "UPDATE", existing.getContractCode());
        return response(requireContract(actor, id), true);
    }

    @Override
    @Transactional
    public ContractResponse updateStatus(Long userId, Long id, ContractStatusRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        BusinessContract existing = requireContract(actor, id);
        requireVersion(request.version(), existing.getVersion());
        String target = normalizeOptional(request.status(), STATUSES, "validation.contract.status.invalid");
        if (target == null || !TRANSITIONS.getOrDefault(existing.getStatus(), List.of()).contains(target)) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.contract.transition.invalid");
        }
        String reason = trimToNull(request.reason());
        if ("TERMINATED".equals(target) && reason == null) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.contract.terminationReason.required");
        }
        if ("ACTIVE".equals(target) && existing.getSignedDate() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.contract.signedDate.requiredForActivation");
        }
        String fulfillment = existing.getFulfillmentStatus();
        if ("COMPLETED".equals(target)) {
            if (!"FULFILLED".equals(fulfillment)) {
                throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.contract.fulfillment.requiredForCompletion");
            }
        }
        int updated = contractMapper.update(null, versionUpdate(actor, existing)
                .set(BusinessContract::getStatus, target)
                .set(BusinessContract::getUpdatedBy, actor.userId())
                .set(BusinessContract::getUpdatedAt, LocalDateTime.now())
                .set(BusinessContract::getVersion, existing.getVersion() + 1));
        if (updated != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        event(actor, id, "STATUS_CHANGED", existing.getStatus(), target, null, reason);
        audit(actor, id, "SET_STATUS", existing.getContractCode() + ":" + existing.getStatus() + "->" + target);
        return response(requireContract(actor, id), true);
    }

    @Override
    @Transactional
    public ContractResponse updateFulfillment(Long userId, Long id, ContractFulfillmentRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        BusinessContract existing = requireContract(actor, id);
        requireVersion(request.version(), existing.getVersion());
        if (!"ACTIVE".equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.contract.fulfillment.activeOnly");
        }
        String target = normalizeOptional(request.status(), FULFILLMENT_STATUSES,
                "validation.contract.fulfillment.invalid");
        if (target == null || !FULFILLMENT_TRANSITIONS
                .getOrDefault(existing.getFulfillmentStatus(), List.of()).contains(target)) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.contract.fulfillment.transition.invalid");
        }
        String reason = trimToNull(request.reason());
        if ("BREACHED".equals(target) && reason == null) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.contract.breachReason.required");
        }
        int updated = contractMapper.update(null, versionUpdate(actor, existing)
                .set(BusinessContract::getFulfillmentStatus, target)
                .set(BusinessContract::getUpdatedBy, actor.userId())
                .set(BusinessContract::getUpdatedAt, LocalDateTime.now())
                .set(BusinessContract::getVersion, existing.getVersion() + 1));
        if (updated != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        event(actor, id, "FULFILLMENT_CHANGED", existing.getFulfillmentStatus(), target, null, reason);
        audit(actor, id, "SET_FULFILLMENT", existing.getContractCode() + ":" + target);
        return response(requireContract(actor, id), true);
    }

    @Override
    @Transactional
    public ContractResponse recordPayment(Long userId, Long id, ContractPaymentRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        BusinessContract existing = requireContract(actor, id);
        requireVersion(request.version(), existing.getVersion());
        if (!"ACTIVE".equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.contract.payment.activeOnly");
        }
        BigDecimal nextPaid = existing.getPaidAmount().add(request.amount());
        if (nextPaid.compareTo(existing.getAmount()) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.contract.payment.exceedsAmount");
        }
        String detail = request.paymentDate() + " · " + request.reference().trim();
        if (StringUtils.hasText(request.note())) detail += " · " + request.note().trim();
        int updated = contractMapper.update(null, versionUpdate(actor, existing)
                .set(BusinessContract::getPaidAmount, nextPaid)
                .set(BusinessContract::getUpdatedBy, actor.userId())
                .set(BusinessContract::getUpdatedAt, LocalDateTime.now())
                .set(BusinessContract::getVersion, existing.getVersion() + 1));
        if (updated != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        event(actor, id, "PAYMENT_RECORDED", existing.getPaidAmount().toPlainString(), nextPaid.toPlainString(),
                request.amount(), detail);
        audit(actor, id, "RECORD_PAYMENT", existing.getContractCode() + ":" + request.amount().toPlainString());
        return response(requireContract(actor, id), true);
    }

    @Override
    @Transactional
    public ContractResponse remind(Long userId, Long id, ContractReminderRequest request) {
        ResolvedUserAccess actor = requireManage(userId);
        BusinessContract existing = requireContract(actor, id);
        requireVersion(request.version(), existing.getVersion());
        String currentExpiryState = expiryState(existing);
        if (!("EXPIRING".equals(currentExpiryState) || "EXPIRED".equals(currentExpiryState))) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.contract.reminder.notDue");
        }
        LocalDateTime now = LocalDateTime.now();
        if (existing.getLastRemindedAt() != null
                && existing.getLastRemindedAt().plus(REMINDER_COOLDOWN).isAfter(now)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "validation.contract.reminder.tooFrequent");
        }
        int updated = contractMapper.update(null, versionUpdate(actor, existing)
                .set(BusinessContract::getReminderCount, existing.getReminderCount() + 1)
                .set(BusinessContract::getLastRemindedAt, now)
                .set(BusinessContract::getUpdatedBy, actor.userId())
                .set(BusinessContract::getUpdatedAt, now)
                .set(BusinessContract::getVersion, existing.getVersion() + 1));
        if (updated != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        event(actor, id, "EXPIRY_REMINDER", null, expiryState(existing), null, null);
        audit(actor, id, "REMIND_EXPIRY", existing.getContractCode());
        notificationService.publish(actor.tenantId(), existing.getOwnerUserId(), NotificationService.TYPE_ALERT,
                message("notification.contract.expiry.title", existing.getContractCode()),
                message("notification.contract.expiry.content", existing.getName(), existing.getEndDate()),
                "contract", existing.getId());
        return response(requireContract(actor, id), true);
    }

    private void validateRequest(ContractRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.contract.dateRange.invalid");
        }
        if (request.signedDate() != null && request.signedDate().isAfter(request.startDate())) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.contract.signedDate.invalid");
        }
        normalizeOptional(request.contractType(), TYPES, "validation.contract.type.invalid");
    }

    private void apply(BusinessContract contract, ContractRequest request, User owner, Supplier supplier) {
        contract.setName(request.name().trim());
        contract.setContractType(request.contractType().trim().toUpperCase(Locale.ROOT));
        contract.setCounterpartyName(request.counterpartyName().trim());
        contract.setSupplierId(request.supplierId());
        contract.setSupplierLabel(supplier == null ? null : supplier.getName());
        contract.setOwnerUserId(owner.getId());
        contract.setOwnerLabel(userLabel(owner));
        contract.setAmount(request.amount());
        contract.setCurrency(request.currency().trim().toUpperCase(Locale.ROOT));
        contract.setSignedDate(request.signedDate());
        contract.setStartDate(request.startDate());
        contract.setEndDate(request.endDate());
        contract.setSummary(trimToNull(request.summary()));
    }

    private User requireOwner(ResolvedUserAccess actor, Long ownerId) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getId, ownerId)
                .eq(User::getTenantId, actor.tenantId()).eq(User::getStatus, 1));
        if (user == null) throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.contract.owner.invalid");
        return user;
    }

    private Supplier requireSupplier(ResolvedUserAccess actor, Long supplierId) {
        if (supplierId == null) return null;
        Supplier supplier = supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>().eq(Supplier::getId, supplierId)
                .eq(Supplier::getTenantId, actor.tenantId()).eq(Supplier::getDeleted, false)
                .eq(Supplier::getStatus, "ACTIVE"));
        if (supplier == null) throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.contract.supplier.invalid");
        return supplier;
    }

    private long count(Long tenantId, String status, LocalDate from, LocalDate to) {
        return contractMapper.selectCount(baseQuery(tenantId)
                .eq(status != null, BusinessContract::getStatus, status)
                .ge(from != null, BusinessContract::getEndDate, from)
                .le(to != null, BusinessContract::getEndDate, to));
    }

    private ContractResponse response(BusinessContract item, boolean canManage) {
        boolean active = "ACTIVE".equals(item.getStatus());
        return new ContractResponse(item.getId(), item.getContractCode(), item.getName(), item.getContractType(),
                item.getCounterpartyName(), item.getSupplierId(), item.getSupplierLabel(), item.getOwnerUserId(), item.getOwnerLabel(),
                item.getAmount(), item.getPaidAmount(), item.getCurrency(), item.getSignedDate(), item.getStartDate(),
                item.getEndDate(), item.getStatus(), item.getFulfillmentStatus(), expiryState(item),
                ChronoUnit.DAYS.between(LocalDate.now(), item.getEndDate()), item.getSummary(), item.getReminderCount(),
                item.getLastRemindedAt(), item.getVersion(), item.getCreatedAt(), item.getUpdatedAt(), canManage,
                canManage ? TRANSITIONS.getOrDefault(item.getStatus(), List.of()) : List.of(),
                canManage && active ? FULFILLMENT_TRANSITIONS
                        .getOrDefault(item.getFulfillmentStatus(), List.of()) : List.of(),
                canManage && active && item.getPaidAmount().compareTo(item.getAmount()) < 0,
                canManage && canRemind(item));
    }

    private String expiryState(BusinessContract item) {
        if (!"ACTIVE".equals(item.getStatus())) return "NONE";
        LocalDate today = LocalDate.now();
        if (item.getEndDate().isBefore(today)) return "EXPIRED";
        if (!item.getEndDate().isAfter(today.plusDays(30))) return "EXPIRING";
        return "NORMAL";
    }

    private boolean canRemind(BusinessContract item) {
        if (!("EXPIRING".equals(expiryState(item)) || "EXPIRED".equals(expiryState(item)))) return false;
        return item.getLastRemindedAt() == null || !item.getLastRemindedAt().plus(REMINDER_COOLDOWN).isAfter(LocalDateTime.now());
    }

    private ContractEventResponse eventResponse(ContractEvent item) {
        return new ContractEventResponse(item.getId(), item.getEventType(), item.getFromValue(), item.getToValue(),
                item.getAmount(), item.getDetail(), item.getOperatorLabel(), item.getCreatedAt());
    }

    private void event(ResolvedUserAccess actor, Long contractId, String type, String from, String to,
                       BigDecimal amount, String detail) {
        ContractEvent event = new ContractEvent();
        event.setTenantId(actor.tenantId()); event.setContractId(contractId); event.setEventType(type);
        event.setFromValue(from); event.setToValue(to); event.setAmount(amount); event.setDetail(detail);
        event.setOperatorId(actor.userId()); event.setOperatorLabel(actor.username()); event.setCreatedAt(LocalDateTime.now());
        eventMapper.insert(event);
    }

    private BusinessContract requireContract(ResolvedUserAccess actor, Long id) {
        BusinessContract contract = contractMapper.selectOne(baseQuery(actor.tenantId()).eq(BusinessContract::getId, id));
        if (contract == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return contract;
    }

    private LambdaQueryWrapper<BusinessContract> baseQuery(Long tenantId) {
        return new LambdaQueryWrapper<BusinessContract>().eq(BusinessContract::getTenantId, tenantId)
                .eq(BusinessContract::getDeleted, false);
    }

    private LambdaUpdateWrapper<BusinessContract> versionUpdate(ResolvedUserAccess actor, BusinessContract existing) {
        return new LambdaUpdateWrapper<BusinessContract>().eq(BusinessContract::getId, existing.getId())
                .eq(BusinessContract::getTenantId, actor.tenantId()).eq(BusinessContract::getDeleted, false)
                .eq(BusinessContract::getVersion, existing.getVersion());
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

    private String normalizeOptional(String value, Set<String> allowed, String messageKey) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new BusinessException(ErrorCode.REQUEST_INVALID, messageKey);
        return normalized;
    }

    private String normalizeCode(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private BigDecimal defaultAmount(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private String userLabel(User user) {
        if (StringUtils.hasText(user.getDisplayName())) return user.getDisplayName();
        if (StringUtils.hasText(user.getEmail())) return user.getEmail();
        return user.getUsername();
    }
    private void requireVersion(Integer actual, Integer expected) {
        if (actual == null || !actual.equals(expected)) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    private void audit(ResolvedUserAccess actor, Long id, String action, String summary) {
        auditService.recordTransactional(actor.tenantId(), actor.userId(), "CONTRACT", String.valueOf(id),
                action, "SUCCESS", summary);
    }
    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
