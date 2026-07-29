package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.AuditQueryService;
import com.aiworkmate.service.LeaveWorkflowService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.aiworkmate.dto.LeaveApprovalContextResponse;
import com.aiworkmate.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        LeaveApplicationController.class,
        TodoController.class,
        ApprovalTaskController.class,
        AuditRecordController.class
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class PhaseOneApiSecurityTest {

    private static final String TOKEN = "phase-one-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeaveWorkflowService leaveWorkflowService;

    @MockBean
    private AuditQueryService auditQueryService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserAccessService userAccessService;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateTokenStatus(TOKEN)).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken(TOKEN)).thenReturn(1001L);
        when(userAccessService.resolveActiveUser(1001L))
                .thenReturn(new ResolvedUserAccess(
                        1001L, "employee@example.com", 1L, "EMPLOYEE",
                        List.of("EMPLOYEE"), List.of("leave:create", "leave:read:self"),
                        List.of("SELF"), 1L));
    }

    @Test
    void shouldRejectAnonymousLeaveCreation() throws Exception {
        mockMvc.perform(post("/api/leave-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLeaveBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));

        verifyNoInteractions(leaveWorkflowService);
    }

    @Test
    void shouldUseAuthenticatedUserIdForLeaveCreation() throws Exception {
        mockMvc.perform(post("/api/leave-applications")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLeaveBody()))
                .andExpect(status().isOk());

        verify(leaveWorkflowService).createDraft(
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectEmployeeGlobalAuditAccess() throws Exception {
        mockMvc.perform(get("/api/audit-records")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PERMISSION_DENIED"));

        verifyNoInteractions(auditQueryService);
    }

    @Test
    void shouldResolveApprovalContextFromAuthenticatedUser() throws Exception {
        when(leaveWorkflowService.approvalContext(1001L))
                .thenReturn(new LeaveApprovalContextResponse(
                        "测试员工", "研发中心", "研发工程师",
                        2001L, "直属主管", "DIRECT_OR_DEPARTMENT_DEFAULT",
                        true, 48));

        mockMvc.perform(get("/api/leave-applications/approval-context")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicantName").value("测试员工"))
                .andExpect(jsonPath("$.data.approverName").value("直属主管"))
                .andExpect(jsonPath("$.data.approvalDueHours").value(48));

        verify(leaveWorkflowService).approvalContext(1001L);
    }

    private String validLeaveBody() {
        return """
                {
                  "leaveType": "PERSONAL",
                  "approverUserId": 2002,
                  "startDate": "2026-07-28",
                  "startPeriod": "AM",
                  "endDate": "2026-07-28",
                  "endPeriod": "PM",
                  "reason": "家庭事务"
                }
                """;
    }
}
