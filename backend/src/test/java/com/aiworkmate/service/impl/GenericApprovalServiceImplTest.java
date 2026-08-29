package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.ApprovalApplicationResponse;
import com.aiworkmate.dto.ApprovalApplicationView;
import com.aiworkmate.dto.ApprovalDraftRequest;
import com.aiworkmate.dto.ApprovalDraftUpdateRequest;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.entity.ApprovalApplication;
import com.aiworkmate.entity.ApprovalForm;
import com.aiworkmate.entity.ApprovalProcess;
import com.aiworkmate.entity.WorkflowInstance;
import com.aiworkmate.entity.WorkflowActionLog;
import com.aiworkmate.entity.WorkflowTask;
import com.aiworkmate.mapper.ApprovalApplicationMapper;
import com.aiworkmate.mapper.ApprovalFormMapper;
import com.aiworkmate.mapper.ApprovalProcessMapper;
import com.aiworkmate.mapper.LeaveApplicationMapper;
import com.aiworkmate.mapper.UserMapper;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenericApprovalServiceImplTest {

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 1001L;

    @Mock private ApprovalApplicationMapper applicationMapper;
    @Mock private ApprovalFormMapper formMapper;
    @Mock private ApprovalProcessMapper processMapper;
    @Mock private LeaveApplicationMapper leaveMapper;
    @Mock private UserMapper userMapper;
    @Mock private WorkflowInstanceMapper instanceMapper;
    @Mock private WorkflowTaskMapper taskMapper;
    @Mock private WorkflowActionLogMapper actionLogMapper;
    @Mock private UserAccessService userAccessService;
    @Mock private BusinessAuditService auditService;
    @Mock private NotificationService notificationService;

    private GenericApprovalServiceImpl service;

    @BeforeEach
    void setUp() {
        initializeTableMetadata(ApprovalApplication.class);
        initializeTableMetadata(ApprovalForm.class);
        initializeTableMetadata(ApprovalProcess.class);
        initializeTableMetadata(WorkflowInstance.class);
        initializeTableMetadata(WorkflowTask.class);
        service = new GenericApprovalServiceImpl(
                applicationMapper, formMapper, processMapper, leaveMapper, userMapper,
                instanceMapper, taskMapper, actionLogMapper, userAccessService,
                auditService, notificationService, new com.fasterxml.jackson.databind.ObjectMapper());
        ReflectionTestUtils.setField(service, "approvalDueHours", 48L);
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(access());
    }

    @Test
    void createDraftAllowsRequiredFieldsToRemainEmptyWithoutStartingWorkflow() {
        when(formMapper.selectOne(any())).thenReturn(form());
        when(applicationMapper.insert(any(ApprovalApplication.class))).thenAnswer(invocation -> {
            ApprovalApplication value = invocation.getArgument(0);
            value.setId(10L);
            return 1;
        });
        when(applicationMapper.selectView(TENANT_ID, 10L)).thenReturn(view("DRAFT", 0));

        ApprovalApplicationResponse response = service.createDraft(USER_ID,
                new ApprovalDraftRequest("expense", null, Map.of()));

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.canEditDraft()).isTrue();
        assertThat(response.canCancel()).isTrue();
        verify(instanceMapper, never()).insert(any(WorkflowInstance.class));
        verify(taskMapper, never()).insert(any(WorkflowTask.class));
    }

    @Test
    void updateDraftRejectsAnApplicationThatWasAlreadySubmitted() {
        ApprovalApplication submitted = application("PENDING", 1);
        when(applicationMapper.selectOne(any())).thenReturn(submitted);

        assertThatThrownBy(() -> service.updateDraft(USER_ID, 10L,
                new ApprovalDraftUpdateRequest(null, Map.of(), 1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("BUSINESS_STATE_INVALID");
    }

    @Test
    void cancelDraftUsesVersionAndMovesToCancelled() {
        when(applicationMapper.selectOne(any())).thenReturn(application("DRAFT", 2));
        when(applicationMapper.update(any(), any())).thenReturn(1);
        when(applicationMapper.selectView(TENANT_ID, 10L)).thenReturn(view("CANCELLED", 3));

        ApprovalApplicationResponse response = service.cancelDraft(USER_ID, 10L, new VersionRequest(2));

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.canEditDraft()).isFalse();
        verify(instanceMapper, never()).insert(any(WorkflowInstance.class));
    }

    @Test
    void submitDraftAtomicallyStartsWorkflowAndCreatesFirstTask() {
        when(applicationMapper.selectOne(any())).thenReturn(application("DRAFT", 2));
        when(formMapper.selectOne(any())).thenReturn(form());
        when(processMapper.selectOne(any())).thenReturn(process());
        when(leaveMapper.resolveApprover(TENANT_ID, USER_ID)).thenReturn(2002L);
        when(applicationMapper.selectGenericDefinitionId(TENANT_ID)).thenReturn(3L);
        when(instanceMapper.insert(any(WorkflowInstance.class))).thenAnswer(invocation -> {
            WorkflowInstance value = invocation.getArgument(0);
            value.setId(4L);
            return 1;
        });
        when(taskMapper.insert(any(WorkflowTask.class))).thenAnswer(invocation -> {
            WorkflowTask value = invocation.getArgument(0);
            value.setId(5L);
            return 1;
        });
        when(applicationMapper.update(any(), any())).thenReturn(1);
        when(applicationMapper.selectView(TENANT_ID, 10L)).thenReturn(view("PENDING", 3));

        ApprovalApplicationResponse response = service.submitDraft(USER_ID, 10L, new VersionRequest(2));

        assertThat(response.status()).isEqualTo("PENDING");
        verify(instanceMapper).insert(any(WorkflowInstance.class));
        verify(taskMapper).insert(any(WorkflowTask.class));
        verify(notificationService).publish(TENANT_ID, 2002L,
                NotificationService.TYPE_APPROVAL, "新的「费用报销」待审批",
                "员工通过发起审批模板提交了申请，请及时处理", "generic-approval", 10L);
    }

    @Test
    void withdrawCancelsApplicationTaskAndInstanceWithOptimisticLocks() {
        ApprovalApplication pending = application("PENDING", 2);
        when(applicationMapper.selectOne(any())).thenReturn(pending);
        when(instanceMapper.selectOne(any())).thenReturn(instance("RUNNING", 0));
        when(taskMapper.selectOne(any())).thenReturn(task("PENDING", 1));
        when(applicationMapper.update(any(), any())).thenReturn(1);
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(instanceMapper.update(any(), any())).thenReturn(1);
        when(applicationMapper.selectView(TENANT_ID, 10L)).thenReturn(view("WITHDRAWN", 3));
        when(actionLogMapper.selectBusinessTimeline(TENANT_ID, "GENERIC_APPROVAL", 10L))
                .thenReturn(List.of());

        ApprovalApplicationResponse response = service.withdraw(USER_ID, 10L, new VersionRequest(2));

        assertThat(response.status()).isEqualTo("WITHDRAWN");
        assertThat(response.canWithdraw()).isFalse();
        verify(taskMapper).update(any(), any());
        verify(instanceMapper).update(any(), any());
        verify(notificationService).publish(TENANT_ID, 2002L,
                NotificationService.TYPE_APPROVAL, "审批申请已撤回",
                "申请人已撤回「费用报销」，原待办已取消", "generic-approval", 10L);
    }

    @Test
    void reopenRejectedApplicationKeepsHistoryAndReturnsEditableDraft() {
        when(applicationMapper.selectOne(any())).thenReturn(application("REJECTED", 3));
        when(applicationMapper.update(any(), any())).thenReturn(1);
        when(applicationMapper.selectView(TENANT_ID, 10L)).thenReturn(view("DRAFT", 4));
        when(actionLogMapper.selectBusinessTimeline(TENANT_ID, "GENERIC_APPROVAL", 10L))
                .thenReturn(List.of());

        ApprovalApplicationResponse response = service.reopen(USER_ID, 10L, new VersionRequest(3));

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.canEditDraft()).isTrue();
        verify(actionLogMapper).insert(any(WorkflowActionLog.class));
        verify(instanceMapper, never()).insert(any(WorkflowInstance.class));
    }

    @Test
    void withdrawRejectsStaleApplicationVersionBeforeChangingWorkflow() {
        when(applicationMapper.selectOne(any())).thenReturn(application("PENDING", 4));

        assertThatThrownBy(() -> service.withdraw(USER_ID, 10L, new VersionRequest(3)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("VERSION_CONFLICT");

        verify(taskMapper, never()).update(any(), any());
        verify(instanceMapper, never()).update(any(), any());
    }

    private ResolvedUserAccess access() {
        return new ResolvedUserAccess(USER_ID, "applicant", TENANT_ID, "EMPLOYEE",
                List.of("EMPLOYEE"), List.of("route:approval-start"), List.of("SELF"), 1L);
    }

    private ApprovalForm form() {
        ApprovalForm form = new ApprovalForm();
        form.setId(1L);
        form.setTenantId(TENANT_ID);
        form.setFormKey("expense");
        form.setFormName("费用报销");
        form.setSchemaJson("{\"fields\":[{\"name\":\"reason\",\"type\":\"text\",\"required\":true}]}");
        form.setStatus("ENABLED");
        form.setDeleted(false);
        return form;
    }

    private ApprovalProcess process() {
        ApprovalProcess process = new ApprovalProcess();
        process.setId(2L);
        process.setTenantId(TENANT_ID);
        process.setFormId(1L);
        process.setProcessKey("expense-approval");
        process.setProcessName("费用审批");
        process.setNodeJson("[{\"nodeName\":\"直属主管\",\"approveType\":\"DIRECT_MANAGER\"}]");
        process.setStatus("ENABLED");
        process.setDeleted(false);
        return process;
    }

    private ApprovalApplication application(String status, int version) {
        ApprovalApplication value = new ApprovalApplication();
        value.setId(10L);
        value.setTenantId(TENANT_ID);
        value.setApplicantUserId(USER_ID);
        value.setFormId(1L);
        value.setProcessId(2L);
        value.setFormKey("expense");
        value.setFormName("费用报销");
        value.setTitle("费用报销");
        value.setDataJson("{\"reason\":\"客户拜访\"}");
        value.setStatus(status);
        value.setVersion(version);
        if (!"DRAFT".equals(status) && !"CANCELLED".equals(status)) {
            value.setWorkflowInstanceId(4L);
        }
        return value;
    }

    private WorkflowInstance instance(String status, int version) {
        WorkflowInstance value = new WorkflowInstance();
        value.setId(4L);
        value.setTenantId(TENANT_ID);
        value.setBusinessType("GENERIC_APPROVAL");
        value.setBusinessId(10L);
        value.setApplicantId(USER_ID);
        value.setStatus(status);
        value.setVersion(version);
        return value;
    }

    private WorkflowTask task(String status, int version) {
        WorkflowTask value = new WorkflowTask();
        value.setId(5L);
        value.setTenantId(TENANT_ID);
        value.setInstanceId(4L);
        value.setBusinessType("GENERIC_APPROVAL");
        value.setBusinessId(10L);
        value.setAssigneeUserId(2002L);
        value.setStatus(status);
        value.setVersion(version);
        return value;
    }

    private ApprovalApplicationView view(String status, int version) {
        LocalDateTime now = LocalDateTime.now();
        return new ApprovalApplicationView(
                10L, USER_ID, "申请人", 1L, "expense", "费用报销", "费用报销",
                "{\"reason\":\"客户拜访\"}", status, version,
                "PENDING".equals(status) ? 5L : null,
                "PENDING".equals(status) ? 0 : null,
                "PENDING".equals(status) ? "PENDING" : null,
                null, "PENDING".equals(status) ? 2002L : null,
                "PENDING".equals(status) ? "审批人" : null,
                "PENDING".equals(status) ? "RUNNING" : null,
                "PENDING".equals(status) ? 4L : null,
                "PENDING".equals(status) ? now : null,
                "CANCELLED".equals(status) ? now : null, now, now);
    }

    private void initializeTableMetadata(Class<?> entityType) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
    }
}
