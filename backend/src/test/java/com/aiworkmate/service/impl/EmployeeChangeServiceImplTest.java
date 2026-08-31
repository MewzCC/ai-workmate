package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.EmployeeChangeDecisionRequest;
import com.aiworkmate.dto.EmployeeChangeRequest;
import com.aiworkmate.dto.EmployeeChangeResponse;
import com.aiworkmate.dto.EmployeeChangeView;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.entity.EmployeeChange;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.EmployeeChangeMapper;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeChangeServiceImplTest {
    private static final long TENANT_ID = 1L;
    private static final long APPLICANT_ID = 1001L;
    private static final long REVIEWER_ID = 2002L;
    private static final long EMPLOYEE_ID = 3003L;

    @Mock private EmployeeChangeMapper changeMapper;
    @Mock private UserMapper userMapper;
    @Mock private AccessControlMapper accessControlMapper;
    @Mock private UserAccessService userAccessService;
    @Mock private BusinessAuditService auditService;
    @Mock private NotificationService notificationService;

    private EmployeeChangeServiceImpl service;

    @BeforeEach
    void setUp() {
        initializeTableMetadata(EmployeeChange.class);
        initializeTableMetadata(User.class);
        service = new EmployeeChangeServiceImpl(changeMapper, userMapper, accessControlMapper,
                userAccessService, auditService, notificationService);
    }

    @Test
    void createSnapshotsCurrentOrganizationAndCreatesPendingApproval() {
        when(userAccessService.resolveActiveUser(APPLICANT_ID)).thenReturn(access(APPLICANT_ID));
        when(userAccessService.resolveActiveUser(REVIEWER_ID)).thenReturn(access(REVIEWER_ID));
        when(userMapper.selectById(EMPLOYEE_ID)).thenReturn(employee());
        when(accessControlMapper.countDepartment(TENANT_ID, 20L)).thenReturn(1);
        when(accessControlMapper.countPosition(TENANT_ID, 30L)).thenReturn(1);
        when(changeMapper.insert(any(EmployeeChange.class))).thenAnswer(invocation -> {
            EmployeeChange value = invocation.getArgument(0);
            value.setId(9L);
            return 1;
        });
        when(changeMapper.selectView(TENANT_ID, 9L)).thenReturn(view("PENDING", 0));

        EmployeeChangeResponse response = service.create(APPLICANT_ID,
                new EmployeeChangeRequest(EMPLOYEE_ID, "TRANSFER", LocalDate.now().plusDays(1),
                        20L, 30L, null, REVIEWER_ID, "业务团队调整"));

        ArgumentCaptor<EmployeeChange> captor = ArgumentCaptor.forClass(EmployeeChange.class);
        verify(changeMapper).insert(captor.capture());
        assertThat(captor.getValue().getCurrentDepartmentId()).isEqualTo(10L);
        assertThat(captor.getValue().getCurrentPositionId()).isEqualTo(11L);
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(response.canWithdraw()).isTrue();
        verify(notificationService).publish(TENANT_ID, REVIEWER_ID,
                NotificationService.TYPE_APPROVAL, "新的员工变动申请",
                "有一笔入转调离申请等待处理", "employee-change", 9L);
    }

    @Test
    void duplicatePendingChangeIsRejectedBeforeInsert() {
        when(userAccessService.resolveActiveUser(APPLICANT_ID)).thenReturn(access(APPLICANT_ID));
        when(userAccessService.resolveActiveUser(REVIEWER_ID)).thenReturn(access(REVIEWER_ID));
        when(userMapper.selectById(EMPLOYEE_ID)).thenReturn(employee());
        when(accessControlMapper.countDepartment(TENANT_ID, 20L)).thenReturn(1);
        when(accessControlMapper.countPosition(TENANT_ID, 30L)).thenReturn(1);
        when(changeMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(APPLICANT_ID,
                new EmployeeChangeRequest(EMPLOYEE_ID, "TRANSFER", LocalDate.now().plusDays(1),
                        20L, 30L, null, REVIEWER_ID, "重复调整")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");

        verify(changeMapper, never()).insert(any(EmployeeChange.class));
    }

    @Test
    void approveTransferRequiresReviewerAndUpdatesEmployeeAtomically() {
        when(userAccessService.resolveActiveUser(REVIEWER_ID)).thenReturn(access(REVIEWER_ID));
        when(changeMapper.selectOne(any())).thenReturn(change("TRANSFER", "PENDING", 2));
        when(userMapper.selectById(EMPLOYEE_ID)).thenReturn(employee());
        when(userMapper.update(any(), any())).thenReturn(1);
        when(changeMapper.update(any(), any())).thenReturn(1);
        when(changeMapper.selectView(TENANT_ID, 9L)).thenReturn(view("EFFECTIVE", 3));

        EmployeeChangeResponse response = service.approve(REVIEWER_ID, 9L,
                new EmployeeChangeDecisionRequest(2, "同意调整"));

        assertThat(response.status()).isEqualTo("EFFECTIVE");
        verify(userMapper).update(any(), any());
        verify(changeMapper).update(any(), any());
        verify(auditService).recordTransactional(TENANT_ID, REVIEWER_ID,
                "EMPLOYEE_CHANGE", "9", "APPROVE", "SUCCESS", "批准员工变动并更新员工状态");
    }

    @Test
    void nonReviewerCannotApprove() {
        when(userAccessService.resolveActiveUser(APPLICANT_ID)).thenReturn(access(APPLICANT_ID));
        when(changeMapper.selectOne(any())).thenReturn(change("TRANSFER", "PENDING", 2));

        assertThatThrownBy(() -> service.approve(APPLICANT_ID, 9L,
                new EmployeeChangeDecisionRequest(2, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_FORBIDDEN");

        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void crossTenantChangeIsHidden() {
        when(userAccessService.resolveActiveUser(REVIEWER_ID)).thenReturn(access(REVIEWER_ID));
        when(changeMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.approve(REVIEWER_ID, 99L,
                new EmployeeChangeDecisionRequest(0, "同意")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");

        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void futureApprovedChangeWaitsForEffectiveDate() {
        when(userAccessService.resolveActiveUser(REVIEWER_ID)).thenReturn(access(REVIEWER_ID));
        EmployeeChange future = change("TRANSFER", "PENDING", 2);
        future.setEffectiveDate(LocalDate.now().plusDays(2));
        when(changeMapper.selectOne(any())).thenReturn(future);
        when(userMapper.selectById(EMPLOYEE_ID)).thenReturn(employee());
        when(changeMapper.update(any(), any())).thenReturn(1);
        when(changeMapper.selectView(TENANT_ID, 9L)).thenReturn(view("APPROVED", 3));

        EmployeeChangeResponse response = service.approve(REVIEWER_ID, 9L,
                new EmployeeChangeDecisionRequest(2, null));

        assertThat(response.status()).isEqualTo("APPROVED");
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void rejectRequiresCommentBeforeStateChange() {
        when(userAccessService.resolveActiveUser(REVIEWER_ID)).thenReturn(access(REVIEWER_ID));

        assertThatThrownBy(() -> service.reject(REVIEWER_ID, 9L,
                new EmployeeChangeDecisionRequest(2, " ")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");

        verify(changeMapper, never()).update(any(), any());
    }

    @Test
    void applicantCanWithdrawPendingRequestWithVersion() {
        when(userAccessService.resolveActiveUser(APPLICANT_ID)).thenReturn(access(APPLICANT_ID));
        when(changeMapper.selectOne(any())).thenReturn(change("OFFBOARDING", "PENDING", 1));
        when(changeMapper.update(any(), any())).thenReturn(1);
        when(changeMapper.selectView(TENANT_ID, 9L)).thenReturn(view("WITHDRAWN", 2));

        EmployeeChangeResponse response = service.withdraw(
                APPLICANT_ID, 9L, new VersionRequest(1));

        assertThat(response.status()).isEqualTo("WITHDRAWN");
        verify(changeMapper).update(any(), any());
    }

    @Test
    void optimisticConflictDoesNotWriteEmployeeChangeAudit() {
        when(userAccessService.resolveActiveUser(APPLICANT_ID)).thenReturn(access(APPLICANT_ID));
        when(changeMapper.selectOne(any())).thenReturn(change("OFFBOARDING", "PENDING", 1));
        when(changeMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.withdraw(
                APPLICANT_ID, 9L, new VersionRequest(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("VERSION_CONFLICT");

        verify(auditService, never()).recordTransactional(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scheduledJobAppliesApprovedChangesOnEffectiveDate() {
        EmployeeChange due = change("OFFBOARDING", "APPROVED", 3);
        when(changeMapper.selectDueForUpdate(any())).thenReturn(List.of(due));
        when(userMapper.selectById(EMPLOYEE_ID)).thenReturn(employee());
        when(userMapper.update(any(), any())).thenReturn(1);
        when(changeMapper.update(any(), any())).thenReturn(1);

        service.applyDueChanges();

        verify(userMapper).update(any(), any());
        verify(changeMapper).update(any(), any());
        verify(auditService).recordTransactional(TENANT_ID, REVIEWER_ID,
                "EMPLOYEE_CHANGE", "9", "APPLY", "SUCCESS",
                "员工变动到达生效日并更新员工状态");
    }

    private ResolvedUserAccess access(long userId) {
        return new ResolvedUserAccess(userId, "admin", TENANT_ID, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), List.of("hr:read", "hr:manage"), List.of("ALL"), 1L);
    }

    private User employee() {
        User user = new User();
        user.setId(EMPLOYEE_ID);
        user.setTenantId(TENANT_ID);
        user.setStatus(1);
        user.setDepartmentId(10L);
        user.setPositionId(11L);
        user.setApproverUserId(12L);
        user.setEmploymentStatus("ACTIVE");
        return user;
    }

    private EmployeeChange change(String type, String status, int version) {
        EmployeeChange value = new EmployeeChange();
        value.setId(9L);
        value.setTenantId(TENANT_ID);
        value.setEmployeeUserId(EMPLOYEE_ID);
        value.setApplicantUserId(APPLICANT_ID);
        value.setReviewApproverUserId(REVIEWER_ID);
        value.setChangeType(type);
        value.setEffectiveDate(LocalDate.now());
        value.setTargetDepartmentId(20L);
        value.setTargetPositionId(30L);
        value.setStatus(status);
        value.setVersion(version);
        return value;
    }

    private EmployeeChangeView view(String status, int version) {
        LocalDateTime now = LocalDateTime.now();
        return new EmployeeChangeView(
                9L, EMPLOYEE_ID, "员工甲", "employee@example.com",
                APPLICANT_ID, "申请人", REVIEWER_ID, "审批人", "TRANSFER",
                LocalDate.now().plusDays(1), 10L, "原部门", 11L, "原岗位",
                12L, "原主管", 20L, "目标部门", 30L, "目标岗位",
                null, null, "业务团队调整", status,
                "APPROVED".equals(status) ? "同意调整" : null, version,
                now, "APPROVED".equals(status) ? now : null,
                "WITHDRAWN".equals(status) ? now : null,
                "EFFECTIVE".equals(status) ? now : null, now, now);
    }

    private void initializeTableMetadata(Class<?> entityType) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
    }
}
