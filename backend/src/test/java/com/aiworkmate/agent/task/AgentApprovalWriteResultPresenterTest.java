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

class AgentApprovalWriteResultPresenterTest {
    private final ObjectMapper json = new ObjectMapper();
    @BeforeEach void messages() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages"); source.setDefaultEncoding("UTF-8");
        new MessageUtils(source).init();
    }
    @ParameterizedTest
    @CsvSource({
        "createDraft,DRAFT,0,Saved general application draft,尚未提交审批",
        "updateDraft,DRAFT,1,Updated general application draft,尚未提交审批",
        "submitDraft,PENDING,1,Awaiting approval,等待审批",
        "cancelDraft,CANCELLED,1,Cancelled general application draft,已取消通用申请草稿",
        "withdraw,WITHDRAWN,1,Withdrew general application,已撤回通用申请",
        "reopen,DRAFT,1,Previous approval history is retained,保留原审批历史",
        "remind,PENDING,1,still pending approval,申请仍待审批"
    })
    void describesTheActualTransitionInBothLanguages(String code,String status,int version,String english,String chinese) {
        var result = json.createObjectNode().put("applicationId",12001).put("formKey","general-form").put("status",status).put("version",version);
        try {
            LocaleContextHolder.setLocale(Locale.US);
            assertThat(AgentReadResultPresenter.present("approval.application."+code,result)).contains(english,"#12001","general-form").doesNotContain("12,001","applicationId:");
            LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
            assertThat(AgentReadResultPresenter.present("approval.application."+code,result)).contains(chinese,"#12001");
        } finally { LocaleContextHolder.resetLocaleContext(); }
    }
    @Test void incompleteOrMismatchedReceiptsAreNotSuccessText() {
        for (var result : java.util.List.of(
                json.createObjectNode().put("applicationId",1).put("version",1).put("status","PENDING"),
                json.createObjectNode().put("applicationId",-1).put("version",1).put("status","PENDING").put("formKey","form"),
                json.createObjectNode().put("applicationId",1).put("version",1).put("status","DRAFT").put("formKey","form"),
                json.createObjectNode().put("applicationId",1).put("version",1).put("status","PENDING").put("formKey","x".repeat(65)),
                json.createObjectNode().put("applicationId",1).put("version",1).put("status","PENDING").put("formKey","form\ncontrol"))) {
            assertThatThrownBy(() -> AgentReadResultPresenter.present("approval.application.submitDraft",result)).isInstanceOf(BusinessException.class);
        }
    }
}
