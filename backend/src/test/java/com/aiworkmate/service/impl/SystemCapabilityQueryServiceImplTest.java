package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.service.SystemCapabilityService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SystemCapabilityQueryServiceImplTest {
    private final UserAccessService accessService = mock(UserAccessService.class);
    private final SystemCapabilityService capabilityService = mock(SystemCapabilityService.class);
    private final SystemCapabilityQueryServiceImpl service =
            new SystemCapabilityQueryServiceImpl(accessService, capabilityService);

    @Test
    void failsClosedUnlessLiveAccessAllowsCapabilityInspection() {
        when(accessService.resolveActiveUser(1L)).thenReturn(null);
        when(accessService.resolveActiveUser(2L)).thenReturn(actor(List.of()));
        assertThatThrownBy(() -> service.inspect(1L)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_REQUIRED.getErrorCode());
        assertThatThrownBy(() -> service.inspect(2L)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PERMISSION_DENIED.getErrorCode());
        verifyNoInteractions(capabilityService);
    }

    @Test
    void delegatesAfterLivePermissionCheck() {
        when(accessService.resolveActiveUser(3L)).thenReturn(actor(List.of("access:manage")));
        service.inspect(3L);
        verify(capabilityService).inspect();
    }

    private ResolvedUserAccess actor(List<String> permissions) {
        return new ResolvedUserAccess(3L, "admin", 9L, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), permissions, List.of("TENANT"), 7L);
    }
}
