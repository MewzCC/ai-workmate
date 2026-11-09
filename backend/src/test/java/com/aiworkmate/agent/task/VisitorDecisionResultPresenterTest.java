package com.aiworkmate.agent.task;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.MessageUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.util.Locale;
import static org.assertj.core.api.Assertions.*;

class VisitorDecisionResultPresenterTest {
    private final ObjectMapper json=new ObjectMapper();
    private final Locale previous=LocaleContextHolder.getLocale();
    private MessageUtils previousMessages;
    @BeforeEach void messages(){previousMessages=(MessageUtils)org.springframework.test.util.ReflectionTestUtils.getField(MessageUtils.class,"instance");var source=new ResourceBundleMessageSource();source.setBasename("i18n/messages");source.setDefaultEncoding("UTF-8");new MessageUtils(source).init();}
    @AfterEach void reset(){LocaleContextHolder.setLocale(previous);org.springframework.test.util.ReflectionTestUtils.setField(MessageUtils.class,"instance",previousMessages);}
    @Test void explainsTaskAndBookingVersionsWithoutPretendingToRegisterTheVisit() throws Exception {
        for(var locale:new Locale[]{Locale.US,Locale.SIMPLIFIED_CHINESE}) {
            LocaleContextHolder.setLocale(locale);
            for(boolean approved:new boolean[]{true,false}) {
                String status=approved?"APPROVED":"REJECTED",action=approved?"APPROVE":"REJECT";
                var receipt=json.createObjectNode().put("resourceId",9).put("resourceType","VISITOR_BOOKING").put("resourceStatus",status)
                        .put("resourceVersion",5).put("taskId",3).put("status",status).put("version",2).put("action",action);
                String code=approved?"visitor.approve":"visitor.reject";
                String text=AgentReadResultPresenter.present(code,receipt);
                assertThat(text).contains("#9","#3",locale.equals(Locale.US)?"task version 2":"任务版本 2").doesNotContain("agent.visitor.");
                assertThat(text).contains(locale.equals(Locale.US)?"No check-in":"签到");
                receipt.put("resourceType","SEAL_USAGE");assertThatThrownBy(()->AgentReadResultPresenter.present(code,receipt)).isInstanceOf(BusinessException.class);
            }
        }
    }
    @Test void queryExplainsTheActionableTaskCoordinateSeparately() throws Exception {
        LocaleContextHolder.setLocale(Locale.US);
        var result=json.readTree("{\"items\":[{\"id\":9,\"visitorName\":\"Owned visitor\",\"status\":\"PENDING\",\"version\":5,\"canDecide\":true,\"taskId\":3,\"taskVersion\":2}],\"total\":1}");
        assertThat(AgentReadResultPresenter.present("visitor.query",result)).contains("booking version: 5","task #3","task version: 2");
        ((com.fasterxml.jackson.databind.node.ObjectNode)result.path("items").get(0)).remove("taskVersion");
        assertThatThrownBy(()->AgentReadResultPresenter.present("visitor.query",result)).isInstanceOf(BusinessException.class);
    }
    @Test void historicalResultsWithoutEitherTaskCoordinateRemainReadableWithoutInventingThem()throws Exception {
        LocaleContextHolder.setLocale(Locale.US);
        var result=json.readTree("{\"items\":[{\"id\":9,\"visitorName\":\"Historical visitor\",\"status\":\"PENDING\",\"version\":5,\"canDecide\":true}],\"total\":1}");
        assertThat(AgentReadResultPresenter.present("visitor.query",result)).contains("Historical visitor","query again").doesNotContain("task #9","agent.visitor.");
    }
}
