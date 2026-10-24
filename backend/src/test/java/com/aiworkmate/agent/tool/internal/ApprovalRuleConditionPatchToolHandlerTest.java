package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApprovalRuleConditionPatchToolHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ApprovalConfigurationToolPort port = mock(ApprovalConfigurationToolPort.class);
    private final TrustedToolContext context = new TrustedToolContext(99L, 7L, 1L, 2L, 1, "trace");
    @Test void preservesLiteralInputAndReturnsOnlyTheActualTypedReceiptWithTrustedIdentity() throws Exception {
        var command = new ApprovalConfigurationToolPort.RuleConditionPatch(11, 3, 2, " keep [literal] ");
        when(port.patchRuleDraftCondition(context.actor(), command)).thenReturn(
                new ApprovalConfigurationToolPort.RuleConditionPatchResult(11, 4, "DISABLED", 2, "leaveType", "eq", " keep [literal] "));
        var args = mapper.readTree("{\"ruleId\":11,\"version\":3,\"conditionNumber\":2,\"value\":\" keep [literal] \"}");
        var handler = new ApprovalRuleConditionPatchToolHandler(port, mapper);
        var result = handler.execute(context, args);
        assertThat(handler.toolCode()).isEqualTo("approval.rule.patchDraftCondition");
        assertThat(result.size()).isEqualTo(7); assertThat(result.path("value").asText()).isEqualTo(" keep [literal] ");
        assertThat(result.path("version").asInt()).isEqualTo(4);
        assertThat(result.toString()).doesNotContain("tenantId", "userId", "conditionJson", "actionJson", "description");
        verify(port).patchRuleDraftCondition(context.actor(), command); verifyNoMoreInteractions(port);
    }
    @Test void malformedArgumentsNeverReachTheDomainPort() throws Exception {
        var handler = new ApprovalRuleConditionPatchToolHandler(port, mapper);
        var valid = mapper.readTree("{\"ruleId\":11,\"version\":3,\"conditionNumber\":2,\"value\":\"ANNUAL\"}");
        for (String field : new String[]{"ruleId", "version", "conditionNumber", "value"}) {
            var missing = valid.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) missing).remove(field);
            assertThatThrownBy(() -> handler.execute(context, missing)).isInstanceOf(BusinessException.class);
        }
        for (String invalid : new String[]{"{\"ruleId\":11,\"version\":2147483647,\"conditionNumber\":2,\"value\":\"1\"}",
                "{\"ruleId\":11,\"version\":3,\"conditionNumber\":11,\"value\":\"1\"}",
                "{\"ruleId\":11,\"version\":3,\"conditionNumber\":2,\"value\":null}",
                "{\"ruleId\":11,\"version\":3,\"conditionNumber\":2,\"value\":false}",
                "{\"ruleId\":11,\"version\":3,\"conditionNumber\":2,\"value\":\" \"}"}) {
            assertThatThrownBy(() -> handler.execute(context, mapper.readTree(invalid))).isInstanceOf(BusinessException.class);
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) valid).put("value", "x".repeat(121));
        assertThatThrownBy(() -> handler.execute(context, valid)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(port);
    }
}
