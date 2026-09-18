package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.ApprovalApplicationResponse;
import com.aiworkmate.dto.ApprovalDraftRequest;
import com.aiworkmate.entity.ApprovalApplication;
import com.aiworkmate.mapper.ApprovalApplicationMapper;
import com.aiworkmate.service.ExpenseApplicationService;
import com.aiworkmate.service.GenericApprovalService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ExpenseAgentDraftCommand;
import com.aiworkmate.service.model.ExpenseAgentDraftReceipt;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExpenseApplicationServiceImpl implements ExpenseApplicationService {
    static final String FORM_KEY = "expense-application";
    private static final Set<String> CATEGORIES =
            Set.of("TRAVEL", "MEAL", "TRANSPORT", "OFFICE", "OTHER");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");

    private final GenericApprovalService approvalService;
    private final ApprovalApplicationMapper applicationMapper;
    private final UserAccessService userAccessService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ExpenseAgentDraftReceipt createAgentDraft(
            Long userId, ExpenseAgentDraftCommand command, String operationKey) {
        requireOperationKey(operationKey);
        ApprovalDraftRequest request = request(command);
        ApprovalApplicationResponse created = approvalService.createAgentDraft(
                userId, request, operationKey);
        return receipt(created);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExpenseAgentDraftReceipt> findAgentDraft(
            Long userId, ExpenseAgentDraftCommand command, String operationKey) {
        requireOperationKey(operationKey);
        ResolvedUserAccess actor = requireAccess(userId);
        ApprovalDraftRequest request = request(command);
        ApprovalApplication existing = applicationMapper.findAgentOperation(
                actor.tenantId(), actor.userId(), operationKey);
        if (existing == null) {
            return Optional.empty();
        }
        requireMatchingDraft(existing, request);
        return Optional.of(new ExpenseAgentDraftReceipt(
                existing.getId(), existing.getFormKey(), existing.getStatus(),
                existing.getVersion(), existing.getCreatedAt()));
    }

    private ApprovalDraftRequest request(ExpenseAgentDraftCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        if (command.amount() != null) {
            if (command.amount().stripTrailingZeros().scale() > 2
                    || command.amount().compareTo(new BigDecimal("0.01")) < 0
                    || command.amount().compareTo(MAX_AMOUNT) > 0) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID);
            }
            fields.put("amount", command.amount());
        }
        String category = normalize(command.category(), 40);
        if (category != null) {
            if (!CATEGORIES.contains(category)) throw new BusinessException(ErrorCode.REQUEST_INVALID);
            fields.put("category", category);
        }
        if (command.expenseDate() != null) fields.put("expenseDate", command.expenseDate().toString());
        String invoiceNumber = normalize(command.invoiceNumber(), 100);
        if (invoiceNumber != null) fields.put("invoiceNumber", invoiceNumber);
        String reason = normalize(command.reason(), 1000);
        if (reason != null) fields.put("reason", reason);
        if (fields.isEmpty()) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        return new ApprovalDraftRequest(FORM_KEY, null, fields);
    }

    private void requireMatchingDraft(ApprovalApplication existing, ApprovalDraftRequest request) {
        JsonNode expected;
        JsonNode actual;
        try {
            expected = objectMapper.readTree(objectMapper.writeValueAsString(request.formData()));
            actual = objectMapper.readTree(existing.getDataJson());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (!FORM_KEY.equals(existing.getFormKey()) || existing.getProcessId() != null
                || !Objects.equals(actual, expected)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
    }

    private ResolvedUserAccess requireAccess(Long userId) {
        ResolvedUserAccess actor = userAccessService.resolveActiveUser(userId);
        if (actor == null || actor.tenantId() == null
                || !actor.permissions().contains("route:approval-start")
                || !actor.permissions().contains("approval:create")) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return actor;
    }

    private ExpenseAgentDraftReceipt receipt(ApprovalApplicationResponse value) {
        return new ExpenseAgentDraftReceipt(
                value.id(), value.formKey(), value.status(), value.version(), value.createdAt());
    }

    private String normalize(String value, int maximumLength) {
        if (value == null) return null;
        String normalized = value.strip();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > maximumLength) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        return normalized;
    }

    private void requireOperationKey(String operationKey) {
        if (operationKey == null || operationKey.isBlank() || operationKey.length() > 128) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
    }
}
