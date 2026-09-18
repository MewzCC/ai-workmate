package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.entity.SealUsage;
import com.aiworkmate.entity.User;
import com.aiworkmate.entity.WorkflowInstance;
import com.aiworkmate.entity.WorkflowTask;
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
import com.aiworkmate.service.model.SealAgentApplicationCommand;
import com.aiworkmate.service.model.SealAgentApplicationReceipt;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SealApplicationServiceTest {
    private static final long TENANT_ID = 1L;
    private static final long ACTOR_ID = 1001L;
    private static final long APPROVER_ID = 2002L;

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
    void agentApplicationUsesTrustedApplicantAndCreatesOneApprovalFlow() {
        stubAccess();
        User applicant = user(ACTOR_ID);
        applicant.setApproverUserId(APPROVER_ID);
        when(userMapper.lockActiveApplicant(TENANT_ID, ACTOR_ID)).thenReturn(applicant);
        when(sealMapper.findAgentOperation(TENANT_ID, ACTOR_ID, "operation")).thenReturn(null);
        when(userMapper.selectById(ACTOR_ID)).thenReturn(applicant);
        when(userMapper.selectById(APPROVER_ID)).thenReturn(user(APPROVER_ID));
        when(sealMapper.selectDefinitionId(TENANT_ID)).thenReturn(11L);
        when(sealMapper.insert(any(SealUsage.class))).thenAnswer(invocation -> {
            invocation.<SealUsage>getArgument(0).setId(31L);
            return 1;
        });
        when(instanceMapper.insert(any(WorkflowInstance.class))).thenAnswer(invocation -> {
            invocation.<WorkflowInstance>getArgument(0).setId(41L);
            return 1;
        });
        when(taskMapper.insert(any(WorkflowTask.class))).thenAnswer(invocation -> {
            invocation.<WorkflowTask>getArgument(0).setId(51L);
            return 1;
        });
        when(sealMapper.update(any(), any())).thenReturn(1);

        SealAgentApplicationReceipt result = service.submitSealUsageAgent(
                ACTOR_ID, command(), "operation");

        assertThat(result).extracting(SealAgentApplicationReceipt::usageId,
                SealAgentApplicationReceipt::status, SealAgentApplicationReceipt::version)
                .containsExactly(31L, "PENDING", 0);
        ArgumentCaptor<SealUsage> usage = ArgumentCaptor.forClass(SealUsage.class);
        verify(sealMapper).insert(usage.capture());
        assertThat(usage.getValue().getApplicantUserId()).isEqualTo(ACTOR_ID);
        assertThat(usage.getValue().getAgentOperationKey()).isEqualTo("operation");
        verify(auditService).recordTransactional(TENANT_ID, ACTOR_ID, "SEAL_USAGE",
                "31", "SUBMIT", "SUCCESS", "Agent 提交用印申请");
    }

    @Test
    void replayReturnsCurrentReceiptAndRejectsChangedCommand() {
        stubAccess();
        when(userMapper.lockActiveApplicant(TENANT_ID, ACTOR_ID)).thenReturn(user(ACTOR_ID));
        SealUsage existing = usage(command());
        existing.setStatus("APPROVED");
        existing.setVersion(1);
        when(sealMapper.findAgentOperation(TENANT_ID, ACTOR_ID, "operation")).thenReturn(existing);

        assertThat(service.submitSealUsageAgent(ACTOR_ID, command(), "operation"))
                .isEqualTo(new SealAgentApplicationReceipt(
                        31, "APPROVED", 1, existing.getSubmittedAt()));
        assertThatThrownBy(() -> service.submitSealUsageAgent(ACTOR_ID,
                new SealAgentApplicationCommand("LEGAL", "另一份文件", "盖章", 1), "operation"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("IDEMPOTENCY_CONFLICT");
        verify(sealMapper, never()).insert(any(SealUsage.class));
        verifyNoInteractions(auditService, notificationService);
    }

    private SealAgentApplicationCommand command() {
        return new SealAgentApplicationCommand("OFFICIAL", "采购合同", "签约", 2);
    }

    private SealUsage usage(SealAgentApplicationCommand command) {
        SealUsage usage = new SealUsage();
        usage.setId(31L);
        usage.setTenantId(TENANT_ID);
        usage.setApplicantUserId(ACTOR_ID);
        usage.setAgentOperationKey("operation");
        usage.setSealType(command.sealType());
        usage.setDocumentTitle(command.documentTitle());
        usage.setUsageReason(command.usageReason());
        usage.setCopies(command.copies());
        usage.setSubmittedAt(LocalDateTime.of(2026, 9, 17, 22, 0));
        return usage;
    }

    private void stubAccess() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(new ResolvedUserAccess(
                ACTOR_ID, "applicant@example.com", TENANT_ID, "EMPLOYEE", List.of("EMPLOYEE"),
                List.of("seal:create"), List.of("SELF"), 1L));
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        user.setTenantId(TENANT_ID);
        user.setStatus(1);
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
