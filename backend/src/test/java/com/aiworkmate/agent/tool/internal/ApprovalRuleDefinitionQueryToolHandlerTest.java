package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApprovalRuleDefinitionQueryToolHandlerTest {
    private final ApprovalConfigurationToolPort port = mock(ApprovalConfigurationToolPort.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final TrustedToolContext context = new TrustedToolContext(99L, 7L, 1L, 2L, 1, "trace");
    @Test void returnsActualTypedRuleValuesUsingOnlyTheTrustedActor() throws Exception {
        when(port.readRuleDefinition(context.actor(), 11L)).thenReturn(new ApprovalConfigurationToolPort.RuleDefinition(
                11L, "fixture", "Fixture", "AMOUNT_THRESHOLD", 20, "DISABLED", 3, "AND",
                List.of(new ApprovalConfigurationToolPort.RuleCondition("amount", "gte", "5000")),
                new ApprovalConfigurationToolPort.RuleAction("FINANCE_REVIEW", false, "OR_SIGN")));
        var output = new ApprovalRuleDefinitionQueryToolHandler(port, mapper).execute(context, mapper.readTree("{\"ruleId\":11}"));
        assertThat(output.path("ruleId").asLong()).isEqualTo(11L);
        assertThat(output.at("/conditions/0/value").asText()).isEqualTo("5000");
        assertThat(output.at("/action/enabled").asBoolean()).isFalse();
        assertThat(output.toString()).doesNotContain("conditionJson", "actionJson", "tenantId", "userId");
        verify(port).readRuleDefinition(context.actor(), 11L);
    }
    @Test void rejectsInvalidIdentitiesBeforeCallingTheDomain() throws Exception {
        var handler = new ApprovalRuleDefinitionQueryToolHandler(port, mapper);
        for (String args : List.of("{}", "{\"ruleId\":0}", "{\"ruleId\":1.5}", "{\"ruleId\":\"11\"}")) {
            assertThatThrownBy(() -> handler.execute(context, mapper.readTree(args))).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(port);
    }
}
