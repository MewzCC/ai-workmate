package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.AiOperationPermissionOverviewResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.AiOperationPermissionService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiOperationPermissionQueryServiceImplTest {
    private final UserAccessService accessService = mock(UserAccessService.class);
    private final AiOperationPermissionService permissionService = mock(AiOperationPermissionService.class);
    private final AiOperationPermissionQueryServiceImpl service =
            new AiOperationPermissionQueryServiceImpl(accessService, permissionService);

    @Test
    void rejectsMissingOrUnauthorizedActorBeforeReadingPolicies() {
        when(accessService.resolveActiveUser(1L)).thenReturn(null);
        when(accessService.resolveActiveUser(2L)).thenReturn(actor(List.of()));

        assertThatThrownBy(() -> service.overview(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_REQUIRED.getErrorCode());
        assertThatThrownBy(() -> service.overview(2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PERMISSION_DENIED.getErrorCode());
        verifyNoInteractions(permissionService);
    }

    @Test
    void rebuildsAuthenticationFromTheLiveAccessSnapshot() {
        when(accessService.resolveActiveUser(3L)).thenReturn(actor(List.of("agent-permission:manage")));
        when(permissionService.overview(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AiOperationPermissionOverviewResponse(
                        new AiOperationPermissionOverviewResponse.RuntimeStatus(true, true, true, false),
                        new AiOperationPermissionOverviewResponse.TenantPolicy(true, false),
                        List.of(), List.of()));

        service.overview(3L);

        var captor = ArgumentCaptor.forClass(AuthenticatedUser.class);
        verify(permissionService).overview(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(9L);
        assertThat(captor.getValue().permissionVersion()).isEqualTo(7L);
        assertThat(captor.getValue().permissions()).containsExactly("agent-permission:manage");
    }

    private ResolvedUserAccess actor(List<String> permissions) {
        return new ResolvedUserAccess(3L, "admin", 9L, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), permissions, List.of("TENANT"), 7L);
    }
}
