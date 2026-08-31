package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.SealReturnRequest;
import com.aiworkmate.dto.SealUsageResponse;
import com.aiworkmate.dto.SealUseRequest;
import com.aiworkmate.entity.SealUsage;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.AssetLedgerMapper;
import com.aiworkmate.mapper.AssetOperationMapper;
import com.aiworkmate.mapper.MeetingBookingMapper;
import com.aiworkmate.mapper.MeetingRoomMapper;
import com.aiworkmate.mapper.SealUsageMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.mapper.VisitorBookingMapper;
import com.aiworkmate.mapper.WorkflowActionLogMapper;
import com.aiworkmate.mapper.WorkflowInstanceMapper;
import com.aiworkmate.mapper.WorkflowTaskMapper;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class SealUsageLifecycleServiceTest {
    private static final long TENANT_ID = 1L;
    private static final long ACTOR_ID = 1001L;
    private static final long USAGE_ID = 66L;

    @Mock private AssetLedgerMapper assetMapper;
    @Mock private AssetOperationMapper operationMapper;
    @Mock private AccessControlMapper accessControlMapper;
    @Mock private MeetingRoomMapper meetingRoomMapper;
    @Mock private MeetingBookingMapper meetingBookingMapper;
    @Mock private VisitorBookingMapper visitorMapper;
    @Mock private SealUsageMapper sealMapper;
    @Mock private UserMapper userMapper;
    @Mock private WorkflowInstanceMapper instanceMapper;
    @Mock private WorkflowTaskMapper taskMapper;
    @Mock private WorkflowActionLogMapper actionLogMapper;
    @Mock private UserAccessService userAccessService;
    @Mock private BusinessAuditService auditService;
    @Mock private NotificationService notificationService;

    private AdminAssetsServiceImpl service;

    @BeforeEach
    void setUp() {
        initializeTableMetadata(SealUsage.class, SealUsageMapper.class);
        service = new AdminAssetsServiceImpl(assetMapper, operationMapper, accessControlMapper,
                meetingRoomMapper, meetingBookingMapper, visitorMapper, sealMapper, userMapper, instanceMapper,
                taskMapper, actionLogMapper, userAccessService, auditService, notificationService);
    }

    @Test
    void registerUsePersistsCopiesHandlerAndAudit() {
        stubAccess(ACTOR_ID, List.of("seal:register"));
        SealUsage before = usage("APPROVED", 2, ACTOR_ID);
        SealUsage after = usage("USED", 3, ACTOR_ID);
        after.setActualCopies(2);
        after.setHandlerUserId(ACTOR_ID);
        after.setUsedAt(LocalDateTime.now());
        when(sealMapper.selectById(USAGE_ID)).thenReturn(before, after);
        when(sealMapper.update(any(), any())).thenReturn(1);
        when(userMapper.selectById(anyLong())).thenReturn(user());

        SealUsageResponse response = service.registerSealUse(ACTOR_ID, USAGE_ID,
                new SealUseRequest(2, 2, "现场核对"));

        assertThat(response.status()).isEqualTo("USED");
        assertThat(response.actualCopies()).isEqualTo(2);
        assertThat(response.handlerUserId()).isEqualTo(ACTOR_ID);
        assertThat(response.canReturn()).isTrue();
        verify(auditService).recordTransactional(TENANT_ID, ACTOR_ID, "SEAL_USAGE",
                Long.toString(USAGE_ID), "USE", "SUCCESS",
                "approvedCopies=2,actualCopies=2,handlerUserId=1001,remark=现场核对");
    }

    @Test
    void returnMovesUsedRequestToReturned() {
        stubAccess(ACTOR_ID, List.of("seal:register"));
        SealUsage before = usage("USED", 3, ACTOR_ID);
        before.setHandlerUserId(ACTOR_ID);
        SealUsage after = usage("RETURNED", 4, ACTOR_ID);
        after.setHandlerUserId(ACTOR_ID);
        after.setReturnedAt(LocalDateTime.now());
        when(sealMapper.selectById(USAGE_ID)).thenReturn(before, after);
        when(sealMapper.update(any(), any())).thenReturn(1);
        when(userMapper.selectById(anyLong())).thenReturn(user());

        SealUsageResponse response = service.returnSeal(ACTOR_ID, USAGE_ID,
                new SealReturnRequest(3, "归还保管柜"));

        assertThat(response.status()).isEqualTo("RETURNED");
        assertThat(response.returnedAt()).isNotNull();
        assertThat(response.canReturn()).isFalse();
    }

    @Test
    void useRequiresApprovedState() {
        stubAccess(ACTOR_ID, List.of("seal:register"));
        when(sealMapper.selectById(USAGE_ID)).thenReturn(usage("PENDING", 1, ACTOR_ID));

        assertThatThrownBy(() -> service.registerSealUse(ACTOR_ID, USAGE_ID,
                new SealUseRequest(1, 1, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");

        verify(sealMapper, never()).update(any(), any());
    }

    @Test
    void actualCopiesCannotExceedApprovedCopies() {
        stubAccess(ACTOR_ID, List.of("seal:register"));
        when(sealMapper.selectById(USAGE_ID)).thenReturn(usage("APPROVED", 2, ACTOR_ID));

        assertThatThrownBy(() -> service.registerSealUse(ACTOR_ID, USAGE_ID,
                new SealUseRequest(2, 3, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUEST_INVALID");

        verify(sealMapper, never()).update(any(), any());
    }

    @Test
    void unrelatedUserRequiresGlobalRegistrationPermission() {
        long unrelatedId = 3003L;
        stubAccess(unrelatedId, List.of("seal:register"));
        when(sealMapper.selectById(USAGE_ID)).thenReturn(usage("APPROVED", 2, ACTOR_ID));

        assertThatThrownBy(() -> service.registerSealUse(unrelatedId, USAGE_ID,
                new SealUseRequest(2, 1, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_FORBIDDEN");
    }

    @Test
    void globalRegistrarCanHandleAnotherApplicantsRequest() {
        long adminId = 4004L;
        stubAccess(adminId, List.of("seal:register", "seal:register:any"));
        SealUsage before = usage("APPROVED", 2, ACTOR_ID);
        SealUsage after = usage("USED", 3, ACTOR_ID);
        after.setActualCopies(1);
        after.setHandlerUserId(adminId);
        after.setUsedAt(LocalDateTime.now());
        when(sealMapper.selectById(USAGE_ID)).thenReturn(before, after);
        when(sealMapper.update(any(), any())).thenReturn(1);
        when(userMapper.selectById(anyLong())).thenReturn(user());

        assertThat(service.registerSealUse(adminId, USAGE_ID,
                new SealUseRequest(2, 1, null)).status()).isEqualTo("USED");
    }

    @Test
    void versionConflictDoesNotWriteExecutionAudit() {
        stubAccess(ACTOR_ID, List.of("seal:register"));
        when(sealMapper.selectById(USAGE_ID)).thenReturn(usage("APPROVED", 2, ACTOR_ID));
        when(sealMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.registerSealUse(ACTOR_ID, USAGE_ID,
                new SealUseRequest(1, 1, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("VERSION_CONFLICT");

        verify(auditService, never()).recordTransactional(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    private void stubAccess(Long userId, List<String> permissions) {
        when(userAccessService.resolveActiveUser(userId)).thenReturn(new ResolvedUserAccess(
                userId, "seal@example.com", TENANT_ID, "EMPLOYEE", List.of("EMPLOYEE"),
                permissions, List.of("SELF"), 1L));
    }

    private SealUsage usage(String status, int version, Long applicantId) {
        SealUsage usage = new SealUsage();
        usage.setId(USAGE_ID);
        usage.setTenantId(TENANT_ID);
        usage.setApplicantUserId(applicantId);
        usage.setApproverUserId(2002L);
        usage.setSealType("OFFICIAL");
        usage.setDocumentTitle("合同文件");
        usage.setUsageReason("项目签约");
        usage.setCopies(2);
        usage.setStatus(status);
        usage.setVersion(version);
        usage.setCreatedAt(LocalDateTime.now().minusDays(1));
        usage.setUpdatedAt(LocalDateTime.now());
        return usage;
    }

    private User user() {
        User user = new User();
        user.setId(ACTOR_ID);
        user.setTenantId(TENANT_ID);
        user.setDisplayName("经办人");
        user.setUsername("handler");
        return user;
    }

    private static void initializeTableMetadata(Class<?> entityClass, Class<?> mapperClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) return;
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        assistant.setCurrentNamespace(mapperClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
