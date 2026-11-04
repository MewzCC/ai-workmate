package com.aiworkmate.agent.task;

import com.aiworkmate.common.MessageUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.util.Locale;
import static org.assertj.core.api.Assertions.assertThat;

class ApprovalMetadataResultPresenterTest {
    @ParameterizedTest @ValueSource(strings = { "zh-CN", "en-US" })
    void presentsParticipantTotalsAndAnExplicitNextPageWithoutClaimingTheWindowIsComplete(String language) throws Exception {
        LocaleContextHolder.setLocale(Locale.forLanguageTag(language));
        String answer = AgentReadResultPresenter.present("approval.task.participants.query", json.readTree("""
                {"items":[{"id":65,"name":"Late-page approver","canApprove":true}],"total":65,"page":3,"size":20,"approversOnly":true}
                """));
        assertThat(answer).contains("65", "3", "4", "Late-page approver").doesNotContain("agent.participants.");
        assertThat(answer).contains(MessageUtils.resolve("agent.participants.nextPage", 4));
    }
    @org.junit.jupiter.api.Test
    void rejectsPartialPaginationMetadataInsteadOfInventingATotal() throws Exception {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> AgentReadResultPresenter.present("approval.task.participants.query", json.readTree("{\"items\":[],\"page\":2}")))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class);
    }
    private final ObjectMapper json = new ObjectMapper();
    @BeforeEach void setup() {
        var source = new ResourceBundleMessageSource(); source.setBasename("i18n/messages"); source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false); new MessageUtils(source).init();
    }
    @AfterEach void cleanup() { LocaleContextHolder.resetLocaleContext(); }
    @ParameterizedTest @ValueSource(strings = { "zh-CN", "en-US" })
    void presentsActualTemplateFieldsAndProcessInsteadOfOnlyASuccessStatus(String language) throws Exception {
        LocaleContextHolder.setLocale(Locale.forLanguageTag(language));
        String answer = AgentReadResultPresenter.present("approval.application.template.query", json.readTree("""
                {"total":1,"items":[{"formId":7,"formKey":"synthetic","formName":"Synthetic form","version":3,
                "fields":[{"name":"reason","label":"Reason","type":"textarea","required":true}],
                "processKey":"review","processName":"Synthetic process","processVersion":2}]}
                """));
        assertThat(answer).contains("Synthetic form", "reason", "Reason", "Synthetic process", "review", "3", "2")
                .doesNotContain("agent.template.").hasSizeLessThanOrEqualTo(1000);
    }
    @ParameterizedTest @ValueSource(strings = { "zh-CN", "en-US" })
    void distinguishesApprovalCandidatesFromCopyOnlyRecipients(String language) throws Exception {
        LocaleContextHolder.setLocale(Locale.forLanguageTag(language));
        String answer = AgentReadResultPresenter.present("approval.task.participants.query", json.readTree("""
                {"items":[{"id":11,"name":"Approver","canApprove":true},{"id":12,"name":"Reader","canApprove":false}]}
                """));
        assertThat(answer).contains("11", "12", "Approver", "Reader", MessageUtils.resolve("agent.participants.copyOnly"));
    }
    @ParameterizedTest @ValueSource(strings = { "zh-CN", "en-US" })
    void presentsProcessVersionAndConcreteNodeNames(String language) throws Exception {
        LocaleContextHolder.setLocale(Locale.forLanguageTag(language));
        String answer = AgentReadResultPresenter.present("approval.process.definition.query", json.readTree("""
                {"processId":9,"processName":"Synthetic process","version":4,"nodes":[
                {"nodeName":"Start","nodeType":"START"},{"nodeName":"Review","nodeType":"APPROVAL","approveType":"USER"}]}
                """));
        assertThat(answer).contains("Synthetic process", "Start", "Review", "USER", "9", "4").doesNotContain("agent.processDefinition.");
    }
}
