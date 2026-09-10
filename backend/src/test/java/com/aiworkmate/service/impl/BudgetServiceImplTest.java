package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.BudgetOperationRequest;
import com.aiworkmate.dto.BudgetPlanRequest;
import com.aiworkmate.dto.BudgetStatusRequest;
import com.aiworkmate.entity.BudgetPlan;
import com.aiworkmate.entity.BudgetTransaction;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.BudgetPlanMapper;
import com.aiworkmate.mapper.BudgetTransactionMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceImplTest {
    @Mock BudgetPlanMapper planMapper; @Mock BudgetTransactionMapper transactionMapper; @Mock UserMapper userMapper;
    @Mock UserAccessService accessService; @Mock BusinessAuditService auditService; @Mock NotificationService notificationService;
    @Mock MessageSource messageSource;
    BudgetServiceImpl service;

    @BeforeEach void setUp(){service=new BudgetServiceImpl(planMapper,transactionMapper,userMapper,accessService,auditService,notificationService,messageSource);}

    @Test void rejectsCreateWithoutManagePermission(){when(accessService.resolveActiveUser(10L)).thenReturn(access(List.of("route:budget")));
        assertThatThrownBy(()->service.create(10L,request(null))).isInstanceOf(BusinessException.class); verifyNoInteractions(planMapper);}

    @Test void createsTenantScopedDraft(){when(accessService.resolveActiveUser(10L)).thenReturn(access(List.of("route:budget","budget:manage")));when(userMapper.selectOne(any())).thenReturn(owner());
        doAnswer(inv->{BudgetPlan p=inv.getArgument(0);p.setId(91L);return 1;}).when(planMapper).insert(any(BudgetPlan.class));
        var result=service.create(10L,request(null)); ArgumentCaptor<BudgetPlan> saved=ArgumentCaptor.forClass(BudgetPlan.class);verify(planMapper).insert(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo(9L);assertThat(saved.getValue().getBudgetCode()).isEqualTo("BUD-001");assertThat(result.status()).isEqualTo("DRAFT");verify(transactionMapper).insert(any(BudgetTransaction.class));}

    @Test void rejectsCrossTenantMissingBudget(){when(accessService.resolveActiveUser(10L)).thenReturn(access(List.of("route:budget","budget:manage")));when(planMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(()->service.operate(10L,99L,new BudgetOperationRequest("OCCUPY",BigDecimal.TEN,null,null,0))).isInstanceOf(BusinessException.class);}

    @Test void rejectsOccupationBeyondAvailable(){stubManage();when(planMapper.selectOne(any())).thenReturn(plan("ACTIVE",0,new BigDecimal("80"),new BigDecimal("10")));
        assertThatThrownBy(()->service.operate(10L,91L,new BudgetOperationRequest("OCCUPY",new BigDecimal("11"),null,null,0))).isInstanceOf(BusinessException.class);verify(planMapper,never()).update(any(),any());}

    @Test void spendsOnlyPreviouslyOccupiedAmount(){stubManage();when(planMapper.selectOne(any())).thenReturn(plan("ACTIVE",0,new BigDecimal("40"),BigDecimal.ZERO));
        assertThatThrownBy(()->service.operate(10L,91L,new BudgetOperationRequest("SPEND",new BigDecimal("50"),null,null,0))).isInstanceOf(BusinessException.class);verify(planMapper,never()).update(any(),any());}

    @Test void closesOnlyAfterOccupationIsCleared(){stubManage();when(planMapper.selectOne(any())).thenReturn(plan("ACTIVE",3,new BigDecimal("1"),BigDecimal.ZERO));
        assertThatThrownBy(()->service.updateStatus(10L,91L,new BudgetStatusRequest("CLOSED",null,3))).isInstanceOf(BusinessException.class);verify(planMapper,never()).update(any(),any());}

    private void stubManage(){when(accessService.resolveActiveUser(10L)).thenReturn(access(List.of("route:budget","budget:manage")));}
    private BudgetPlanRequest request(Integer version){return new BudgetPlanRequest("bud-001","研发预算",2026,10L,new BigDecimal("100"),"CNY",80,"说明",version);}
    private BudgetPlan plan(String status,int version,BigDecimal occupied,BigDecimal spent){BudgetPlan p=new BudgetPlan();p.setId(91L);p.setTenantId(9L);p.setBudgetCode("BUD-001");p.setName("研发预算");p.setFiscalYear(2026);p.setOwnerUserId(10L);p.setOwnerLabel("员工");p.setTotalAmount(new BigDecimal("100"));p.setOccupiedAmount(occupied);p.setSpentAmount(spent);p.setCurrency("CNY");p.setWarningThreshold(80);p.setStatus(status);p.setVersion(version);p.setDeleted(false);p.setUpdatedAt(LocalDateTime.now());return p;}
    private User owner(){User u=new User();u.setId(10L);u.setTenantId(9L);u.setUsername("employee");u.setDisplayName("员工");u.setEmail("employee@example.invalid");u.setStatus(1);return u;}
    private ResolvedUserAccess access(List<String> permissions){return new ResolvedUserAccess(10L,"employee",9L,"FINANCE_ADMIN",List.of("FINANCE_ADMIN"),permissions,List.of("TENANT"),2L);}
}
