package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalRuleCreateDraftToolHandlerTest {
    @Test
    void mapsSemanticRuleAndUsesTrustedActor() throws Exception {
        var port = mock(ApprovalConfigurationToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(99L, 7L, 10L, 20L, 0, "trace");
        when(port.createRuleDraft(eq(context.actor()), any())).thenReturn(
                new ApprovalConfigurationToolPort.RuleDraftResult(
                        51L, "large-expense", "DISABLED", 1, LocalDateTime.of(2026, 10, 1, 23, 30)));

        var result = new ApprovalRuleCreateDraftToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"ruleKey":"large-expense","ruleName":"大额费用复核","ruleType":"AMOUNT_THRESHOLD",
                 "priority":10,"logic":"AND","conditions":[{"field":"amount","operator":"gte","value":"5000"}],
                 "action":{"appendNode":"FINANCE_REVIEW","enabled":true,"mode":"OR_SIGN"}}
                """));

        assertThat(result.path("status").asText()).isEqualTo("DISABLED");
        var command = org.mockito.ArgumentCaptor.forClass(ApprovalConfigurationToolPort.RuleDraft.class);
        verify(port).createRuleDraft(eq(context.actor()), command.capture());
        assertThat(command.getValue().conditions()).hasSize(1);
        assertThat(command.getValue().action().appendNode()).isEqualTo("FINANCE_REVIEW");
    }
}
