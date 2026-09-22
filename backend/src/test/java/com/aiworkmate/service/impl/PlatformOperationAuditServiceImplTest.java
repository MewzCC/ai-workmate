package com.aiworkmate.service.impl;

import com.aiworkmate.dto.AuthUserResponse;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.entity.PlatformOperationLog;
import com.aiworkmate.mapper.PlatformOperationLogMapper;
import com.aiworkmate.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PlatformOperationAuditServiceImplTest {
    @Mock PlatformOperationLogMapper mapper;

    @Test
    void recordsAuthenticatedHumanOperationWithoutPayloadOrQueryString() {
        PlatformOperationAuditServiceImpl service = new PlatformOperationAuditServiceImpl(mapper);
        AuthenticatedUser actor = new AuthenticatedUser(7L, "operator@example.com", 9L,
                "SYSTEM_ADMIN", List.of("SYSTEM_ADMIN"), List.of("audit:read"), List.of("TENANT"), 1L);

        service.recordRequest(actor, "post", "/api/contracts/42", 200, 35,
                "10.0.0.8", "Browser\nInjected", null);

        ArgumentCaptor<PlatformOperationLog> captor = ArgumentCaptor.forClass(PlatformOperationLog.class);
        verify(mapper).insert(captor.capture());
        PlatformOperationLog record = captor.getValue();
        assertThat(record.getTenantId()).isEqualTo(9L);
        assertThat(record.getUserId()).isEqualTo(7L);
        assertThat(record.getEventType()).isEqualTo("HTTP_WRITE");
        assertThat(record.getHttpMethod()).isEqualTo("POST");
        assertThat(record.getRequestPath()).isEqualTo("/api/contracts/42");
        assertThat(record.getOutcome()).isEqualTo("SUCCEEDED");
        assertThat(record.getUserAgent()).isEqualTo("Browser Injected");
        assertThat(record.getRequestPath()).doesNotContain("?");
    }

    @Test
    void hashesRejectedLoginIdentityAndNeverStoresCredentialInput() {
        PlatformOperationAuditServiceImpl service = new PlatformOperationAuditServiceImpl(mapper);

        service.recordLoginFailure("person@example.com", "PASSWORD", 12,
                "127.0.0.1", "Browser", new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED));

        ArgumentCaptor<PlatformOperationLog> captor = ArgumentCaptor.forClass(PlatformOperationLog.class);
        verify(mapper).insert(captor.capture());
        PlatformOperationLog record = captor.getValue();
        assertThat(record.getTenantId()).isNull();
        assertThat(record.getUserId()).isNull();
        assertThat(record.getActorLabel()).startsWith("account:")
                .doesNotContain("person@example.com")
                .doesNotContain("secret-password");
        assertThat(record.getEventType()).isEqualTo("LOGIN");
        assertThat(record.getOutcome()).isEqualTo("REJECTED");
        assertThat(record.getStatusCode()).isEqualTo(423);
        assertThat(record.getErrorCode()).isEqualTo("AUTH_ACCOUNT_LOCKED");
    }

    @Test
    void recordsSuccessfulLoginAgainstResolvedTenantAndUser() {
        PlatformOperationAuditServiceImpl service = new PlatformOperationAuditServiceImpl(mapper);
        AuthUserResponse actor = new AuthUserResponse(7L, "张三", "person@example.com", 9L,
                "EMPLOYEE", List.of("EMPLOYEE"), null, List.of(), List.of("SELF"), 1L);

        service.recordLoginSuccess(actor, "EMAIL_CODE", 8, "127.0.0.1", "Browser");

        ArgumentCaptor<PlatformOperationLog> captor = ArgumentCaptor.forClass(PlatformOperationLog.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue()).satisfies(record -> {
            assertThat(record.getTenantId()).isEqualTo(9L);
            assertThat(record.getUserId()).isEqualTo(7L);
            assertThat(record.getActorLabel()).isEqualTo("张三");
            assertThat(record.getEventType()).isEqualTo("LOGIN");
            assertThat(record.getOutcome()).isEqualTo("SUCCEEDED");
        });
    }

    @Test
    void auditStorageFailureDoesNotReplaceTheBusinessResponse() {
        PlatformOperationAuditServiceImpl service = new PlatformOperationAuditServiceImpl(mapper);
        doThrow(new IllegalStateException("database unavailable")).when(mapper)
                .insert(any(PlatformOperationLog.class));
        AuthenticatedUser actor = new AuthenticatedUser(7L, "operator", 9L,
                "EMPLOYEE", List.of("EMPLOYEE"), List.of(), List.of("SELF"), 1L);

        assertThatCode(() -> service.recordRequest(actor, "GET", "/api/todos", 200,
                1, "127.0.0.1", "Browser", null)).doesNotThrowAnyException();
    }
}
