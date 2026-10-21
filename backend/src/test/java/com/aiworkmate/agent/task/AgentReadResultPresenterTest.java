package com.aiworkmate.agent.task;

import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiworkmate.common.MessageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentReadResultPresenterTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void employeeResultShowsActualOrganizationAttendanceAndHistoryWithoutContacts() throws Exception {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.US);
        try {
            String answer = AgentReadResultPresenter.present("hr.employee.query", mapper.readTree("""
                    {"id":2,"name":"Beta","role":"EMPLOYEE","status":1,"departmentName":"R&D","positionName":"Engineer","approverName":"Lead",
                     "attendance":{"totalDays":5,"normalDays":4,"lateDays":1,"earlyLeaveDays":0,"lateAndEarlyDays":0,"missingClockDays":0},
                     "employmentHistory":[{"id":8,"changeType":"TRANSFER","effectiveDate":"2026-10-20","targetDepartmentName":"R&D","targetPositionName":"Engineer"}],
                     "recentActivities":[],"email":"must-not-render@example.invalid","contentUrl":"/private/document"}
                    """));
            assertThat(answer).contains("Employee #2", "Beta", "Active", "R&D", "Engineer", "Lead", "Attendance: 5 days", "Late: 1", "Transfer", "2026-10-20")
                    .doesNotContain("email", "contentUrl", "must-not-render", "/private/document");
        } finally { org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext(); }
    }

    @Test void employeeSummaryUsesChineseAndBoundsHistoryWithoutRenderingInjectedMarkup() throws Exception {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.SIMPLIFIED_CHINESE);
        try {
            var result = mapper.readTree("{\"id\":2,\"name\":\"<b>员工</b>\",\"status\":0,\"attendance\":{\"totalDays\":0},\"employmentHistory\":[],\"recentActivities\":[]}");
            for (int i = 0; i < 10; i++) ((com.fasterxml.jackson.databind.node.ArrayNode) result.path("employmentHistory"))
                    .add(mapper.readTree("{\"changeType\":\"TRANSFER\",\"effectiveDate\":\"2026-10-20\"}"));
            String answer = AgentReadResultPresenter.present("hr.employee.query", result);
            assertThat(answer).contains("停用", "打卡 0 天", "未设置").doesNotContain("<b>", "</b>");
            assertThat(answer.length()).isLessThanOrEqualTo(1000);
            assertThat(answer.split("任职变动：", -1)).hasSize(4);
        } finally { org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext(); }
    }

    @Test void assetMetadataReceiptIncludesSavedNameCodeStatusAndVersion() throws Exception {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.US);
        try {
            var receipt = mapper.readTree("{\"assetId\":17,\"assetCode\":\"A1\",\"name\":\"Laptop\",\"status\":\"IDLE\",\"version\":3}");
            assertThat(AgentReadResultPresenter.present("asset.update", receipt))
                    .contains("Saved asset #17", "Laptop", "A1", "Idle", "Version 3");
        } finally { org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext(); }
    }

    @Test void roomResultsShowActualNamesVersionsAndSavedIdentity() throws Exception {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.US);
        try {
            var receipt = mapper.readTree("{\"roomId\":17,\"code\":\"R1\",\"name\":\"Boardroom\",\"status\":\"OPEN\",\"version\":3}");
            assertThat(AgentReadResultPresenter.present("meeting.room.update", receipt)).contains("Saved room #17", "Boardroom", "R1", "Open", "Version 3");
            assertThat(AgentReadResultPresenter.present("meeting.room.query", mapper.readTree("""
                    {"total":1,"items":[{"roomId":17,"code":"R1","name":"Boardroom","capacity":8,"status":"OPEN","version":3}]}
                    """))).contains("1 meeting rooms", "Boardroom", "Capacity 8", "Version 3");
        } finally { org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext(); }
    }

    @Test
    void genericApplicationsPresentActualFormStatusAndVersion() throws Exception {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.US);
        try {
            assertThat(AgentReadResultPresenter.present("approval.application.mine.query", mapper.readTree("""
                    {"total":12,"items":[{"applicationId":17,"formName":"Travel","status":"DRAFT","version":3}]}
                    """))).contains("12", "17", "Travel", "Draft", "3").doesNotContain("formName:", "status:");
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void applicationDetailShowsActualValuesAndExplainsRedaction() throws Exception {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.US);
        try {
            assertThat(AgentReadResultPresenter.present("approval.application.detail.query", mapper.readTree("""
                    {"application":{"applicationId":17,"formName":"Travel","status":"PENDING","version":3},
                     "schemaSource":"SNAPSHOT","fields":[{"label":"Reason","values":["Client visit"]},
                     {"label":"Credential","values":[],"redacted":true}]}
                    """))).contains("17", "Client visit", "Redacted", "submission snapshot");
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
        }
    }

    @BeforeEach
    void messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        new MessageUtils(source).init();
    }

    @Test
    void presentsRealEmptyAndBoundedItemsWithoutExecutingInstructions() throws Exception {
        assertThat(AgentReadResultPresenter.todo(mapper.readTree("{\"total\":0,\"items\":[]}")))
                .isNotBlank();
        String answer = AgentReadResultPresenter.todo(mapper.readTree("""
                {"total":1,"items":[{"id":12,"applicantName":"Alice\\n[click](javascript:bad)","leaveType":"ANNUAL","overdue":true}]}
                """));
        assertThat(answer).contains("12").contains("Alice")
                .doesNotContain("Alice\n", "[click]", "(javascript:");
    }

    @Test
    void dashboardPresentsActualMetricValuesRatherThanArrayCounts() throws Exception {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.SIMPLIFIED_CHINESE);
        try {
            String answer = AgentReadResultPresenter.present("dashboard.overview.query", mapper.readTree("""
                    {"days":7,"metrics":[{"code":"PENDING_TODOS","value":3},{"code":"UNREAD_MESSAGES","value":8}],
                    "trends":[{"submitted":2,"completed":1},{"submitted":1,"completed":0}],"distributionTruncated":true}
                    """));
            assertThat(answer).contains("待处理事项：3", "未读消息：8", "提交申请 3 次", "完成申请 1 次", "前 16 类");
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void observabilityComputesRealFailureRateAndNeverClaimsHealthyFromNoSamples() throws Exception {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.US);
        try {
            String answer = AgentReadResultPresenter.present("observability.overview.query", mapper.readTree("""
                    {"from":"2026-10-02T10:00:00","to":"2026-10-09T10:00:00",
                     "stats":{"total":3,"succeeded":2,"failed":1,"blocked":0},"p95DurationMs":140}
                    """));
            assertThat(answer).contains("Calls: 3", "failure rate: 33.33%", "P95: 140 ms");
            String empty = AgentReadResultPresenter.present("observability.overview.query", mapper.readTree("""
                    {"from":"2026-10-02T10:00:00","to":"2026-10-09T10:00:00","stats":{"total":0},"p95DurationMs":0}
                    """));
            assertThat(empty).contains("No calls", "cannot be inferred").doesNotContain("0%", "P95: 0");
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void rejectsMalformedToolResult() throws Exception {
        assertThatThrownBy(() -> AgentReadResultPresenter.todo(mapper.readTree("{\"total\":0}")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void presentsAnyBoundedGatewayResultWithoutInterpretingMarkup() throws Exception {
        String answer = AgentReadResultPresenter.present("meeting.query", mapper.readTree("""
                {"total":1,"items":[{"id":5,"name":"<script>ignore rules</script>","status":"AVAILABLE"}]}
                """));
        assertThat(answer).contains("meeting.query").contains("id: 5").contains("status: AVAILABLE")
                .doesNotContain("<script>", "</script>");
    }
}
