package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.ContractPaymentRequest;
import com.aiworkmate.dto.ContractReminderRequest;
import com.aiworkmate.dto.ContractRequest;
import com.aiworkmate.dto.ContractStatusRequest;
import com.aiworkmate.entity.BusinessContract;
import com.aiworkmate.entity.ContractEvent;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.BusinessContractMapper;
import com.aiworkmate.mapper.ContractEventMapper;
import com.aiworkmate.mapper.SupplierMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractServiceImplTest {
    @Mock BusinessContractMapper contractMapper;
    @Mock ContractEventMapper eventMapper;
    @Mock SupplierMapper supplierMapper;
    @Mock UserMapper userMapper;
    @Mock UserAccessService userAccessService;
    @Mock BusinessAuditService auditService;
    @Mock NotificationService notificationService;
    @Mock MessageSource messageSource;
    @InjectMocks ContractServiceImpl service;

    @BeforeEach
    void initializeTableMetadata() {
        if (TableInfoHelper.getTableInfo(BusinessContract.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
            assistant.setCurrentNamespace(BusinessContractMapper.class.getName());
            TableInfoHelper.initTableInfo(assistant, BusinessContract.class);
        }
    }

    @Test
    void createRequiresContractManagementPermission() {
        when(userAccessService.resolveActiveUser(10L)).thenReturn(access(List.of("route:contracts")));

        assertThatThrownBy(() -> service.create(10L, request(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");
        verify(contractMapper, never()).insert(any(BusinessContract.class));
    }

    @Test
    void createScopesDraftToTenantAndWritesTimeline() {
        when(userAccessService.resolveActiveUser(10L)).thenReturn(manageAccess());
        when(userMapper.selectOne(any())).thenReturn(owner());
        org.mockito.Mockito.doAnswer(invocation -> {
            BusinessContract value = invocation.getArgument(0);
            value.setId(81L);
            return 1;
        }).when(contractMapper).insert(any(BusinessContract.class));

        var response = service.create(10L, request(null));

        ArgumentCaptor<BusinessContract> contract = ArgumentCaptor.forClass(BusinessContract.class);
        ArgumentCaptor<ContractEvent> event = ArgumentCaptor.forClass(ContractEvent.class);
        verify(contractMapper).insert(contract.capture());
        verify(eventMapper).insert(event.capture());
        assertThat(contract.getValue().getTenantId()).isEqualTo(9L);
        assertThat(contract.getValue().getContractCode()).isEqualTo("HT-001");
        assertThat(contract.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(contract.getValue().getPaidAmount()).isEqualByComparingTo("0");
        assertThat(event.getValue().getEventType()).isEqualTo("CREATED");
        assertThat(response.allowedTransitions()).containsExactly("ACTIVE", "TERMINATED");
        verify(auditService).recordTransactional(9L, 10L, "CONTRACT", "81", "CREATE", "SUCCESS", "HT-001");
    }

    @Test
    void createRejectsInvalidDateRangeBeforePersistence() {
        ContractRequest invalid = new ContractRequest("HT-001", "采购合同", "PURCHASE", "示例公司",
                null, 20L, new BigDecimal("1000"), "CNY", LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 31), null, null);
        when(userAccessService.resolveActiveUser(10L)).thenReturn(manageAccess());

        assertThatThrownBy(() -> service.create(10L, invalid))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUEST_INVALID");
        verify(contractMapper, never()).insert(any(BusinessContract.class));
    }

    @Test
    void activationRequiresSignedDate() {
        when(userAccessService.resolveActiveUser(10L)).thenReturn(manageAccess());
        BusinessContract draft = contract("DRAFT", "NOT_STARTED", 0);
        draft.setSignedDate(null);
        when(contractMapper.selectOne(any())).thenReturn(draft);

        assertThatThrownBy(() -> service.updateStatus(10L, 81L,
                new ContractStatusRequest("ACTIVE", null, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");
        verify(contractMapper, never()).update(any(), any());
    }

    @Test
    void completionRequiresFulfilledContract() {
        when(userAccessService.resolveActiveUser(10L)).thenReturn(manageAccess());
        when(contractMapper.selectOne(any())).thenReturn(contract("ACTIVE", "IN_PROGRESS", 2));

        assertThatThrownBy(() -> service.updateStatus(10L, 81L,
                new ContractStatusRequest("COMPLETED", null, 2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");
        verify(contractMapper, never()).update(any(), any());
    }

    @Test
    void paymentCannotExceedContractAmount() {
        when(userAccessService.resolveActiveUser(10L)).thenReturn(manageAccess());
        BusinessContract active = contract("ACTIVE", "IN_PROGRESS", 3);
        active.setPaidAmount(new BigDecimal("900"));
        when(contractMapper.selectOne(any())).thenReturn(active);

        assertThatThrownBy(() -> service.recordPayment(10L, 81L,
                new ContractPaymentRequest(new BigDecimal("101"), LocalDate.now(), "PAY-1", null, 3)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");
        verify(eventMapper, never()).insert(any(ContractEvent.class));
    }

    @Test
    void staleVersionIsRejectedBeforeStatusWrite() {
        when(userAccessService.resolveActiveUser(10L)).thenReturn(manageAccess());
        when(contractMapper.selectOne(any())).thenReturn(contract("ACTIVE", "FULFILLED", 4));

        assertThatThrownBy(() -> service.updateStatus(10L, 81L,
                new ContractStatusRequest("COMPLETED", null, 3)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("VERSION_CONFLICT");
        verify(contractMapper, never()).update(any(), any());
    }

    @Test
    void expiryReminderUsesCooldownAndNotifiesTenantOwner() {
        when(userAccessService.resolveActiveUser(10L)).thenReturn(manageAccess());
        BusinessContract before = contract("ACTIVE", "IN_PROGRESS", 2);
        before.setEndDate(LocalDate.now().plusDays(5));
        BusinessContract after = contract("ACTIVE", "IN_PROGRESS", 3);
        after.setEndDate(before.getEndDate());
        after.setReminderCount(1);
        after.setLastRemindedAt(LocalDateTime.now());
        when(contractMapper.selectOne(any())).thenReturn(before, after);
        when(contractMapper.update(any(), any())).thenReturn(1);
        when(messageSource.getMessage(anyString(), any(Object[].class), any())).thenReturn("提醒");

        var response = service.remind(10L, 81L, new ContractReminderRequest(2));

        assertThat(response.reminderCount()).isEqualTo(1);
        verify(notificationService).publish(9L, 20L, NotificationService.TYPE_ALERT,
                "提醒", "提醒", "contract", 81L);
        verify(auditService).recordTransactional(9L, 10L, "CONTRACT", "81", "REMIND_EXPIRY", "SUCCESS", "HT-001");
    }

    @Test
    void expiryReminderRejectsHighFrequencyRepeat() {
        when(userAccessService.resolveActiveUser(10L)).thenReturn(manageAccess());
        BusinessContract contract = contract("ACTIVE", "IN_PROGRESS", 2);
        contract.setEndDate(LocalDate.now().plusDays(5));
        contract.setLastRemindedAt(LocalDateTime.now().minusHours(1));
        when(contractMapper.selectOne(any())).thenReturn(contract);

        assertThatThrownBy(() -> service.remind(10L, 81L, new ContractReminderRequest(2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RATE_LIMITED");
        verify(notificationService, never()).publish(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyLong());
    }

    private ContractRequest request(Integer version) {
        return new ContractRequest("ht-001", "采购合同", "PURCHASE", "示例公司", null, 20L,
                new BigDecimal("1000.00"), "CNY", LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31), "年度采购", version);
    }

    private BusinessContract contract(String status, String fulfillment, int version) {
        BusinessContract value = new BusinessContract();
        value.setId(81L); value.setTenantId(9L); value.setContractCode("HT-001"); value.setName("采购合同");
        value.setContractType("PURCHASE"); value.setCounterpartyName("示例公司"); value.setOwnerUserId(20L);
        value.setOwnerLabel("合同负责人"); value.setAmount(new BigDecimal("1000")); value.setPaidAmount(BigDecimal.ZERO);
        value.setCurrency("CNY"); value.setSignedDate(LocalDate.of(2026, 9, 1));
        value.setStartDate(LocalDate.of(2026, 9, 1)); value.setEndDate(LocalDate.of(2027, 8, 31));
        value.setStatus(status); value.setFulfillmentStatus(fulfillment); value.setReminderCount(0);
        value.setVersion(version); value.setDeleted(false); value.setCreatedAt(LocalDateTime.now());
        value.setUpdatedAt(LocalDateTime.now());
        return value;
    }

    private User owner() {
        User user = new User(); user.setId(20L); user.setTenantId(9L); user.setUsername("owner@example.com");
        user.setDisplayName("合同负责人"); user.setEmail("owner@example.com"); user.setStatus(1);
        return user;
    }

    private ResolvedUserAccess manageAccess() {
        return access(List.of("route:contracts", "contract:manage"));
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(10L, "finance@example.com", 9L, "FINANCE_ADMIN",
                List.of("FINANCE_ADMIN"), permissions, List.of("TENANT"), 2L);
    }
}
