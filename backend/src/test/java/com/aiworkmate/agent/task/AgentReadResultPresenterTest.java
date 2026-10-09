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
