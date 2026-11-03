package com.aiworkmate.agent.task;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.MessageUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.util.Locale;
import static org.assertj.core.api.Assertions.*;

class AgentAtomicResultPresenterTest {
    private final ObjectMapper json = new ObjectMapper();
    @BeforeEach void messages() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages"); source.setDefaultEncoding("UTF-8");
        new MessageUtils(source).init();
    }
    @Test void describesActualPendingReceiptsInBothLanguages() {
        try {
            for (Locale locale : new Locale[]{Locale.US, Locale.SIMPLIFIED_CHINESE}) {
                LocaleContextHolder.setLocale(locale);
                for (String code : new String[]{"approval.application.apply", "expense.apply"}) {
                    var receipt = json.createObjectNode().put("applicationId", 37).put("formKey", "expense-application")
                            .put("status", "PENDING").put("version", 1);
                    String text = AgentReadResultPresenter.present(code, receipt);
                    assertThat(text).contains("37", "1").doesNotContain("agent.");
                    assertThat(text).contains(locale.equals(Locale.US) ? "not approval" : "尚未审批通过");
                }
            }
        } finally { LocaleContextHolder.resetLocaleContext(); }
    }
    @Test void rejectsMalformedReceiptsInsteadOfReportingSuccess() {
        var receipt = json.createObjectNode().put("applicationId", 37).put("formKey", "expense-application")
                .put("status", "DRAFT").put("version", 0);
        assertThatThrownBy(() -> AgentReadResultPresenter.present("expense.apply", receipt)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AgentReadResultPresenter.present("approval.application.apply", receipt)).isInstanceOf(BusinessException.class);
        receipt.put("status", "PENDING").put("version", 1).put("formKey", "another-form");
        assertThatThrownBy(() -> AgentReadResultPresenter.present("expense.apply", receipt)).isInstanceOf(BusinessException.class);
    }
    @Test void pagePreparationTextDoesNotClaimBrowserApplicationOrBusinessSubmission() {
        LocaleContextHolder.setLocale(Locale.US);
        try {
            String text = AgentReadResultPresenter.present("page.ui.prepare", json.createObjectNode()
                    .put("pageId", "dictionary").put("code", "ui.fillForm"));
            assertThat(text).contains("dictionary", "ui.fillForm", "browser must apply", "cannot submit");
        } finally { LocaleContextHolder.resetLocaleContext(); }
    }
}
