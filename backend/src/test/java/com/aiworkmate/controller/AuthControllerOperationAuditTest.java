package com.aiworkmate.controller;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.AuthUserResponse;
import com.aiworkmate.dto.EmailCodeLoginRequest;
import com.aiworkmate.dto.PasswordLoginRequest;
import com.aiworkmate.security.AuthCookieManager;
import com.aiworkmate.service.AuthService;
import com.aiworkmate.service.PlatformOperationAuditService;
import com.aiworkmate.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerOperationAuditTest {
    @Mock AuthService authService;
    @Mock PlatformOperationAuditService auditService;
    @Mock JwtUtil jwtUtil;
    @Mock AuthCookieManager cookieManager;

    @Test
    void recordsResolvedUserAfterSuccessfulPasswordLogin() {
        AuthController controller = controller();
        PasswordLoginRequest request = new PasswordLoginRequest(
                "person@example.com", "secret-password", null, null, true);
        AuthUserResponse user = user();
        MockHttpServletRequest servletRequest = request("10.0.0.7", "Desktop Browser");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authService.loginWithPassword(request, "10.0.0.7")).thenReturn(user);
        when(jwtUtil.generateToken(user.id(), user.email(), user.role())).thenReturn("token");

        var result = controller.passwordLogin(request, servletRequest, response);

        assertThat(result.getData()).isEqualTo(user);
        verify(cookieManager).write(response, "token", true);
        verify(auditService).recordLoginSuccess(eq(user), eq("PASSWORD"), anyLong(),
                eq("10.0.0.7"), eq("Desktop Browser"));
        verify(auditService, never()).recordLoginFailure(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordsRejectedEmailCodeLoginWithoutEstablishingSession() {
        AuthController controller = controller();
        EmailCodeLoginRequest request = new EmailCodeLoginRequest(
                "person@example.com", "123456", false);
        MockHttpServletRequest servletRequest = request("10.0.0.8", "Mobile Browser");
        MockHttpServletResponse response = new MockHttpServletResponse();
        BusinessException failure = new BusinessException(ErrorCode.AUTH_CODE_INVALID);
        when(authService.loginWithEmailCode(request)).thenThrow(failure);

        assertThatThrownBy(() -> controller.emailCodeLogin(request, servletRequest, response))
                .isSameAs(failure);

        verify(auditService).recordLoginFailure(eq("person@example.com"), eq("EMAIL_CODE"),
                anyLong(), eq("10.0.0.8"), eq("Mobile Browser"), eq(failure));
        verify(cookieManager, never()).write(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    private AuthController controller() {
        return new AuthController(authService, auditService, jwtUtil, cookieManager);
    }

    private MockHttpServletRequest request(String address, String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        request.addHeader("User-Agent", userAgent);
        return request;
    }

    private AuthUserResponse user() {
        return new AuthUserResponse(7L, "张三", "person@example.com", 9L,
                "EMPLOYEE", List.of("EMPLOYEE"), null, List.of(), List.of("SELF"), 1L);
    }
}
