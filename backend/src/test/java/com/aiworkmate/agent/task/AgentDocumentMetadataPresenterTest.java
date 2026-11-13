package com.aiworkmate.agent.task;

import com.aiworkmate.common.MessageUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.util.Locale;
import static org.assertj.core.api.Assertions.assertThat;

class AgentDocumentMetadataPresenterTest {
    @Test void explainsActualMetadataAndEmptyPagesInBothLanguagesWithoutCoordinates() throws Exception {
        var source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages"); source.setDefaultEncoding("UTF-8");
        new MessageUtils(source).init();
        var json = new ObjectMapper();
        var result = json.readTree("""
                {"items":[{"documentId":"9007199254740993","documentType":"CONTRACT","displayName":"Quarterly.pdf",
                "mimeType":"application/pdf","fileSize":1234,"createdAt":"2026-11-13T12:00:00"}],"total":1,"page":1,"size":20}
                """);
        try {
            for (var locale : new Locale[]{Locale.US, Locale.SIMPLIFIED_CHINESE}) {
                LocaleContextHolder.setLocale(locale);
                String summary = AgentReadResultPresenter.present("hr.employee.documents.query", result);
                assertThat(summary).contains("9007199254740993", "Quarterly.pdf", "1234", "application/pdf", "2026-11-13")
                        .doesNotContain("agent.documents.", "storageKey", "contentUrl", "uploadedBy");
                assertThat(summary).contains(locale.equals(Locale.US) ? "Contract" : "合同");
                var empty = json.readTree("{\"items\":[],\"total\":25,\"page\":3,\"size\":20}");
                assertThat(AgentReadResultPresenter.present("seal.documents.query", empty)).contains("25");
            }
        } finally { LocaleContextHolder.resetLocaleContext(); }
    }
}
