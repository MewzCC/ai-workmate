package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalRuleUpdateDraftToolHandlerTest {
    @Test
    void mapsVersionBoundSemanticUpdateAndUsesTrustedActor() throws Exception {
        var port = mock(ApprovalConfigurationToolPort.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(99L, 7L, 10L, 20L, 0, "trace");
        var expected = new ApprovalConfigurationToolPort.RuleDraftResult(
                51L, "large-expense", "DISABLED", 3, LocalDateTime.of(2026, 10, 1, 23, 50));
        var command = new ApprovalConfigurationToolPort.RuleDraftUpdate(
                51L, 2, "大额费用复核（新版）", "AMOUNT_THRESHOLD", 5, null, "AND",
                java.util.List.of(new ApprovalConfigurationToolPort.RuleCondition("amount", "gte", "8000")),
                new ApprovalConfigurationToolPort.RuleAction("FINANCE_REVIEW", true, "OR_SIGN"));
        when(port.updateRuleDraft(context.actor(), command)).thenReturn(expected);

        var result = new ApprovalRuleUpdateDraftToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"ruleId":51,"version":2,"ruleName":"大额费用复核（新版）","ruleType":"AMOUNT_THRESHOLD",
                 "priority":5,"logic":"AND","conditions":[{"field":"amount","operator":"gte","value":"8000"}],
                 "action":{"appendNode":"FINANCE_REVIEW","enabled":true,"mode":"OR_SIGN"}}
                """));

        assertThat(result.path("version").asInt()).isEqualTo(3);
        verify(port).updateRuleDraft(eq(context.actor()), eq(command));
    }
}
