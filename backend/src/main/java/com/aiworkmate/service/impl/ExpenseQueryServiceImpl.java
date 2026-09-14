package com.aiworkmate.service.impl;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.ApprovalApplicationResponse;
import com.aiworkmate.dto.ExpenseSummaryResponse;
import com.aiworkmate.service.ExpenseQueryService;
import com.aiworkmate.service.GenericApprovalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ExpenseQueryServiceImpl implements ExpenseQueryService {
    private static final String FORM_KEY = "expense-application";
    private final GenericApprovalService genericApprovalService;
    private final ObjectMapper objectMapper;

    @Override @Transactional(readOnly = true)
    public PageResponse<ExpenseSummaryResponse> mine(Long userId, String status, int page, int size) {
        var result = genericApprovalService.mine(userId, status, FORM_KEY, page, size);
        return PageResponse.of(result.records().stream().map(this::summary).toList(),
                result.total(), result.page(), result.size());
    }

    @Override @Transactional(readOnly = true)
    public ExpenseSummaryResponse detail(Long userId, Long applicationId) {
        ApprovalApplicationResponse application = genericApprovalService.detail(userId, applicationId);
        if (!FORM_KEY.equals(application.formKey())) {
            throw new com.aiworkmate.common.BusinessException(com.aiworkmate.common.ErrorCode.RESOURCE_NOT_FOUND);
        }
        return summary(application);
    }

    private ExpenseSummaryResponse summary(ApprovalApplicationResponse item) {
        JsonNode data;
        try { data = objectMapper.readTree(item.dataJson()); } catch (Exception ignored) { data = objectMapper.createObjectNode(); }
        return new ExpenseSummaryResponse(item.id(), item.title(), decimal(data, "amount"), text(data, "category"),
                date(data, "expenseDate"), text(data, "invoiceNumber"), text(data, "reason"), item.status(),
                item.version(), item.taskAssigneeName(), item.taskDueAt(), item.submittedAt(), item.overdue(),
                item.canRemind(), item.canWithdraw(), item.canEditDraft(), item.canCancel());
    }
    private String text(JsonNode node, String field) { return node.path(field).isTextual() ? node.path(field).asText() : null; }
    private BigDecimal decimal(JsonNode node, String field) { return node.path(field).isNumber() ? node.path(field).decimalValue() : null; }
    private LocalDate date(JsonNode node, String field) { try { return LocalDate.parse(text(node, field)); } catch (Exception ignored) { return null; } }
}
