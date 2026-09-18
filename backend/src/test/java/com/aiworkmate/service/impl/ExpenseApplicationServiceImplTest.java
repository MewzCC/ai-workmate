package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.ApprovalApplicationResponse;
import com.aiworkmate.dto.ApprovalDraftRequest;
import com.aiworkmate.entity.ApprovalApplication;
import com.aiworkmate.mapper.ApprovalApplicationMapper;
import com.aiworkmate.service.GenericApprovalService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ExpenseAgentDraftCommand;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseApplicationServiceImplTest {
    @Mock private GenericApprovalService approvalService;
    @Mock private ApprovalApplicationMapper applicationMapper;
    @Mock private UserAccessService userAccessService;

    private ExpenseApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ExpenseApplicationServiceImpl(
                approvalService, applicationMapper, userAccessService, new ObjectMapper());
    }

    @Test
    void createsFixedExpenseDraftWithoutAcceptingFormOrIdentityFields() {
        ApprovalApplicationResponse response = org.mockito.Mockito.mock(ApprovalApplicationResponse.class);
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 18, 12, 0);
        when(response.id()).thenReturn(51L);
        when(response.formKey()).thenReturn("expense-application");
        when(response.status()).thenReturn("DRAFT");
        when(response.version()).thenReturn(0);
        when(response.createdAt()).thenReturn(createdAt);
        when(approvalService.createAgentDraft(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("expense-operation"))).thenReturn(response);

        var receipt = service.createAgentDraft(7L, command(), "expense-operation");

        ArgumentCaptor<ApprovalDraftRequest> captor = ArgumentCaptor.forClass(ApprovalDraftRequest.class);
        verify(approvalService).createAgentDraft(
                org.mockito.ArgumentMatchers.eq(7L), captor.capture(),
                org.mockito.ArgumentMatchers.eq("expense-operation"));
        assertThat(captor.getValue().formKey()).isEqualTo("expense-application");
        assertThat(captor.getValue().processKey()).isNull();
        assertThat(captor.getValue().formData()).containsOnlyKeys(
                "amount", "category", "expenseDate", "invoiceNumber", "reason");
        assertThat(receipt.applicationId()).isEqualTo(51L);
        assertThat(receipt.status()).isEqualTo("DRAFT");
    }

    @Test
    void verifiesHistoricalDraftWithoutDependingOnCurrentFormConfiguration() {
        when(userAccessService.resolveActiveUser(7L)).thenReturn(access());
        ApprovalApplication existing = existing("{\"amount\":88.50,\"category\":\"TRAVEL\","
                + "\"expenseDate\":\"2026-09-17\",\"invoiceNumber\":\"INV-1\","
                + "\"reason\":\"客户拜访\"}");
        existing.setStatus("PENDING");
        existing.setVersion(1);
        when(applicationMapper.findAgentOperation(1L, 7L, "expense-operation"))
                .thenReturn(existing);

        var result = service.findAgentDraft(7L, command(), "expense-operation");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().status()).isEqualTo("PENDING");
    }

    @Test
    void verificationRejectsPermissionRevocationAndOperationKeyCollision() {
        when(userAccessService.resolveActiveUser(7L)).thenReturn(new ResolvedUserAccess(
                7L, "user", 1L, "EMPLOYEE", List.of("EMPLOYEE"),
                List.of("route:approval-start"), List.of("SELF"), 2L));
        assertThatThrownBy(() -> service.findAgentDraft(7L, command(), "expense-operation"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");

        when(userAccessService.resolveActiveUser(7L)).thenReturn(access());
        when(applicationMapper.findAgentOperation(1L, 7L, "expense-operation"))
                .thenReturn(existing("{\"reason\":\"其他报销\"}"));
        assertThatThrownBy(() -> service.findAgentDraft(7L, command(), "expense-operation"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    private ExpenseAgentDraftCommand command() {
        return new ExpenseAgentDraftCommand(
                new BigDecimal("88.50"), "TRAVEL", LocalDate.of(2026, 9, 17),
                "INV-1", "客户拜访");
    }

    private ResolvedUserAccess access() {
        return new ResolvedUserAccess(
                7L, "user", 1L, "EMPLOYEE", List.of("EMPLOYEE"),
                List.of("route:approval-start", "approval:create"), List.of("SELF"), 1L);
    }

    private ApprovalApplication existing(String dataJson) {
        ApprovalApplication value = new ApprovalApplication();
        value.setId(51L);
        value.setTenantId(1L);
        value.setApplicantUserId(7L);
        value.setFormKey("expense-application");
        value.setDataJson(dataJson);
        value.setStatus("DRAFT");
        value.setVersion(0);
        value.setCreatedAt(LocalDateTime.of(2026, 9, 18, 12, 0));
        return value;
    }
}
