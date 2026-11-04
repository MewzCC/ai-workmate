package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.ApprovalDraftRequest;
import com.aiworkmate.dto.ApprovalDraftUpdateRequest;
import com.aiworkmate.dto.ApprovalSubmitRequest;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.mapper.ApprovalApplicationMapper;
import com.aiworkmate.mapper.ApprovalFormMapper;
import com.aiworkmate.mapper.ApprovalProcessMapper;
import com.aiworkmate.mapper.ApprovalRuleMapper;
import com.aiworkmate.mapper.LeaveApplicationMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.mapper.WorkflowActionLogMapper;
import com.aiworkmate.mapper.WorkflowInstanceMapper;
import com.aiworkmate.mapper.WorkflowTaskMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GenericApprovalWritePermissionTest {
    enum Operation {
        CREATE("approval:create"), UPDATE("approval:create"), SUBMIT_DRAFT("approval:submit"),
        CANCEL("approval:cancel"), WITHDRAW("approval:withdraw"), REMIND("approval:remind"),
        REOPEN("approval:reopen"), SUBMIT("approval:submit");
        final String permission;
        Operation(String permission) { this.permission = permission; }
        void invoke(GenericApprovalServiceImpl service) {
            switch (this) {
                case CREATE -> service.createDraft(1L, new ApprovalDraftRequest("expense", null, Map.of()));
                case UPDATE -> service.updateDraft(1L, 10L, new ApprovalDraftUpdateRequest(null, Map.of(), 0));
                case SUBMIT_DRAFT -> service.submitDraft(1L, 10L, new VersionRequest(0));
                case CANCEL -> service.cancelDraft(1L, 10L, new VersionRequest(0));
                case WITHDRAW -> service.withdraw(1L, 10L, new VersionRequest(0));
                case REMIND -> service.remind(1L, 10L, new VersionRequest(0));
                case REOPEN -> service.reopen(1L, 10L, new VersionRequest(0));
                case SUBMIT -> service.submit(1L, new ApprovalSubmitRequest("expense", null, Map.of()));
            }
        }
    }

    @ParameterizedTest @EnumSource(Operation.class)
    void routeVisibilityDoesNotAuthorizeBusinessWrites(Operation operation) {
        deniedBeforeResourceAccess(operation, List.of("route:approval-start"));
    }

    @ParameterizedTest @EnumSource(Operation.class)
    void businessPermissionDoesNotAuthorizeMissingRoute(Operation operation) {
        deniedBeforeResourceAccess(operation, List.of(operation.permission, "approval:create"));
    }

    private void deniedBeforeResourceAccess(Operation operation, List<String> permissions) {
        var applications = mock(ApprovalApplicationMapper.class);
        var forms = mock(ApprovalFormMapper.class);
        var processes = mock(ApprovalProcessMapper.class);
        var rules = mock(ApprovalRuleMapper.class);
        var leaves = mock(LeaveApplicationMapper.class);
        var users = mock(UserMapper.class);
        var instances = mock(WorkflowInstanceMapper.class);
        var tasks = mock(WorkflowTaskMapper.class);
        var logs = mock(WorkflowActionLogMapper.class);
        var access = mock(UserAccessService.class);
        var audit = mock(BusinessAuditService.class);
        var notifications = mock(NotificationService.class);
        when(access.resolveActiveUser(1L)).thenReturn(new ResolvedUserAccess(1L, "reader", 1L,
                "EMPLOYEE", List.of("EMPLOYEE"), permissions, List.of("SELF"), 1L));
        var service = new GenericApprovalServiceImpl(applications, forms, processes, rules, leaves, users,
                instances, tasks, logs, access, audit, notifications, new ObjectMapper());
        assertThatThrownBy(() -> operation.invoke(service)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");
        verifyNoInteractions(applications, forms, processes, rules, leaves, users, instances, tasks, logs, audit, notifications);
    }
}
