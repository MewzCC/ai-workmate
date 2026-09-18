package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.AccessControlOverviewResponse;
import com.aiworkmate.service.AccessControlService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccessGovernanceQueryServiceImplTest {
    private final UserAccessService accessService = mock(UserAccessService.class);
    private final AccessControlService accessControlService = mock(AccessControlService.class);
    private final AccessGovernanceQueryServiceImpl service =
            new AccessGovernanceQueryServiceImpl(accessService, accessControlService);

    @Test
    void rejectsMissingOrUnauthorizedActorBeforeReadingOverview() {
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
        verifyNoInteractions(accessControlService);
    }

    @Test
    void readsOnlyTheResolvedActorsTenant() {
        var response = new AccessControlOverviewResponse(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(accessService.resolveActiveUser(3L)).thenReturn(actor(List.of("access:manage")));
        when(accessControlService.overview(9L)).thenReturn(response);

        service.overview(3L);

        verify(accessControlService).overview(9L);
    }

    private ResolvedUserAccess actor(List<String> permissions) {
        return new ResolvedUserAccess(3L, "admin", 9L, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), permissions, List.of("TENANT"), 7L);
    }
}
