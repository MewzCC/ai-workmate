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
    void rejectsMalformedToolResult() throws Exception {
        assertThatThrownBy(() -> AgentReadResultPresenter.todo(mapper.readTree("{\"total\":0}")))
                .isInstanceOf(BusinessException.class);
    }
}
