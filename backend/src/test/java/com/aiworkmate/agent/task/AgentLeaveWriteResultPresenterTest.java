package com.aiworkmate.agent.task;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.MessageUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.util.Locale;
import static org.assertj.core.api.Assertions.*;

class AgentLeaveWriteResultPresenterTest {
    private final ObjectMapper json=new ObjectMapper();
    @BeforeEach void messages() {
        var source=new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");source.setDefaultEncoding("UTF-8");
        new MessageUtils(source).init();
    }
    @ParameterizedTest
    @CsvSource({
        "createDraft,DRAFT,0,Saved leave draft,尚未提交审批",
        "updateDraft,DRAFT,1,Updated leave draft,尚未提交审批",
        "submit,PENDING,1,Awaiting approval,等待审批",
        "apply,PENDING,1,approval task #22002,审批待办 #22002",
        "withdraw,WITHDRAWN,1,Withdrew leave application,已撤回请假申请",
        "remind,PENDING,1,still pending approval,申请仍待审批"
    })
    void summarizesOnlyTheActualWriteAndPreservesCopyableIdentifiers(String code,String status,int version,String english,String chinese) {
        var result=json.createObjectNode().put("applicationId",12001).put("status",status).put("version",version);
        if("apply".equals(code))result.put("approvalTaskId",22002);
        try {
            LocaleContextHolder.setLocale(Locale.US);
            assertThat(AgentReadResultPresenter.present("leave."+code,result)).contains(english,"#12001").doesNotContain("12,001","applicationId:");
            LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
            assertThat(AgentReadResultPresenter.present("leave."+code,result)).contains(chinese,"#12001").doesNotContain("12,001");
        } finally {LocaleContextHolder.resetLocaleContext();}
    }
    @Test void incompleteOrMismatchedReceiptsCannotBecomeSuccessText() {
        for(var result:java.util.List.of(
                json.createObjectNode().put("applicationId",1).put("version",1).put("status","PENDING"),
                json.createObjectNode().put("applicationId",-1).put("version",1).put("status","PENDING").put("approvalTaskId",2),
                json.createObjectNode().put("applicationId",1).put("version",1).put("status","DRAFT").put("approvalTaskId",2))) {
            assertThatThrownBy(()->AgentReadResultPresenter.present("leave.apply",result)).isInstanceOf(BusinessException.class);
        }
    }
}
