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
class GenericCollaborationResultPresenterTest {
    @Test void bothReceiptsExplainConcreteRecipientsAndNeverClaimApproval()throws Exception{
        var json=new ObjectMapper();var previous=LocaleContextHolder.getLocale();
        try{
            for(var language:new Locale[]{Locale.US,Locale.SIMPLIFIED_CHINESE})for(var suffix:new String[]{"transfer","cc"}){
                LocaleContextHolder.setLocale(language);
                var result=json.createObjectNode().put("applicationId",1).put("taskId",2).put("status","PENDING").put("version",3).put("applicationStatus","PENDING")
                        .put("action",suffix.equals("transfer")?"TRANSFER":"CC").put("originalAssigneeUserId",4).put("targetUserId",5).put("assigneeUserId",suffix.equals("transfer")?5:4);
                var text=AgentReadResultPresenter.present("approval.genericTask."+suffix,result);
                assertThat(text).contains("#2","4","5","3").doesNotContain("agent.genericTask.");
                assertThat(text).contains(suffix.equals("transfer")?(language.equals(Locale.US)?"still pending":"仍待审批"):(language.equals(Locale.US)?"read-only":"只读"));
                result.put("assigneeUserId",99);
                assertThatThrownBy(()->AgentReadResultPresenter.present("approval.genericTask."+suffix,result)).isInstanceOf(BusinessException.class);
            }
        }finally{LocaleContextHolder.setLocale(previous);}
    }
}
