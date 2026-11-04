package com.aiworkmate.agent.task;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.MessageUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Locale;
import static org.assertj.core.api.Assertions.*;

class UnifiedTodoResultPresenterTest {
    private final ObjectMapper json=new ObjectMapper();
    private MessageUtils previous;
    private Locale locale;
    @BeforeEach void init(){previous=(MessageUtils)ReflectionTestUtils.getField(MessageUtils.class,"instance");locale=LocaleContextHolder.getLocale();var source=new ResourceBundleMessageSource();source.setBasename("i18n/messages");source.setDefaultEncoding("UTF-8");new MessageUtils(source).init();}
    @AfterEach void restore(){ReflectionTestUtils.setField(MessageUtils.class,"instance",previous);LocaleContextHolder.setLocale(locale);}
    private com.fasterxml.jackson.databind.JsonNode result() throws Exception{return json.readTree("""
            {"total":2,"page":1,"size":20,"businessType":"ALL","status":"PENDING","items":[
            {"id":3,"applicationId":9,"applicantName":"Applicant A","businessType":"LEAVE_APPLICATION","title":"LV-000009","leaveType":"PERSONAL","durationHalfDays":2,"status":"PENDING","version":0,"overdue":false,"detailAvailable":true},
            {"id":4,"applicationId":10,"applicantName":"Applicant B","businessType":"GENERIC_APPROVAL","title":"Frozen expense","nodeName":"Finance review","status":"PENDING","version":1,"overdue":true,"detailAvailable":false}]}
            """);}
    @Test void printsBothBusinessKindsAndRealNodeTextInChineseAndEnglish() throws Exception {
        for(Locale language:new Locale[]{Locale.SIMPLIFIED_CHINESE,Locale.US}){LocaleContextHolder.setLocale(language);String text=AgentReadResultPresenter.todo(result());assertThat(text).contains("Applicant A","Applicant B","Frozen expense","Finance review","#3","#4").doesNotContain("agent.todo.","agent.result.enum.");assertThat(text).contains(language.equals(Locale.US)?"Generic approval task":"通用申请任务");}
    }
    @Test void anEmptyFilteredQueryDescribesItsActualScopeAndPage() throws Exception {
        LocaleContextHolder.setLocale(Locale.US);String text=AgentReadResultPresenter.todo(json.readTree("{\"total\":0,\"items\":[],\"page\":3,\"size\":10,\"businessType\":\"GENERIC_APPROVAL\",\"status\":\"APPROVED\",\"from\":\"2026-11-01T00:00:00\"}"));assertThat(text).contains("No tasks","Generic applications","page 3","2026-11-01").doesNotContain("no pending");
    }
    @Test void rejectsInvalidNewScopeInsteadOfInventingATaskList() throws Exception {
        var value=(com.fasterxml.jackson.databind.node.ObjectNode)result();value.put("businessType","ARBITRARY_DOMAIN");assertThatThrownBy(()->AgentReadResultPresenter.todo(value)).isInstanceOf(BusinessException.class);
    }
}
