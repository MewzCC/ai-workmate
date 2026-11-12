package com.aiworkmate.agent.task;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.LocalizedMessagesExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.i18n.LocaleContextHolder;
import java.util.Locale;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(LocalizedMessagesExtension.class)
class GenericAddSignResultPresenterTest {
    @Test void reportsTheActualPendingOrderInBothLanguagesAndRejectsContradictoryReceipts() {
        var json = new ObjectMapper(); var previous = LocaleContextHolder.getLocale();
        try {
            for (var language : new Locale[]{Locale.US, Locale.SIMPLIFIED_CHINESE}) {
                LocaleContextHolder.setLocale(language);
                for (String mode : new String[]{"PRE", "POST"}) {
                    boolean pre = mode.equals("PRE");
                    var result = json.createObjectNode().put("applicationId", 1).put("taskId", 2).put("version", 3)
                            .put("status", pre ? "WAITING" : "PENDING").put("applicationStatus", "PENDING")
                            .put("action", "ADD_SIGN").put("mode", mode).put("targetUserId", 4).put("addedTaskId", 5)
                            .put("addedTaskStatus", pre ? "PENDING" : "WAITING").put("addedTaskVersion", 0).put("activeTaskId", pre ? 5 : 2);
                    var text = AgentReadResultPresenter.present("approval.genericTask.addSign", result);
                    assertThat(text).contains("#2", "#5", "4", "3").doesNotContain("agent.genericTask.");
                    assertThat(text).contains(language.equals(Locale.US) ? (pre ? "still pending" : "has not completed") : "审批");
                    for (String key : new String[]{"activeTaskId", "addedTaskId"}) {
                        var wrong = result.deepCopy().put(key, 2);
                        if (key.equals("activeTaskId") && !pre) wrong.put(key, 5);
                        assertThatThrownBy(() -> AgentReadResultPresenter.present("approval.genericTask.addSign", wrong)).isInstanceOf(BusinessException.class);
                    }
                    assertThatThrownBy(() -> AgentReadResultPresenter.present("approval.genericTask.addSign", result.deepCopy().put("addedTaskStatus", pre ? "WAITING" : "PENDING"))).isInstanceOf(BusinessException.class);
                }
            }
        } finally { LocaleContextHolder.setLocale(previous); }
    }
}
