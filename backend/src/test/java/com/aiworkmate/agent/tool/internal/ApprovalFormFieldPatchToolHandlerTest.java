package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.AgentApprovalFormFieldPatchToolDefinitions;
import com.aiworkmate.agent.registry.ToolDefinition;
import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApprovalFormFieldPatchToolHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ApprovalConfigurationToolPort port = mock(ApprovalConfigurationToolPort.class);
    private final TrustedToolContext context = new TrustedToolContext(99L, 7L, 1L, 2L, 1, "trace");
    @Test void returnsSchemaValidActualValuesAndForwardsOnlyTrustedIdentity() throws Exception {
        var command = new ApprovalConfigurationToolPort.FormFieldPatch(11, 3, "reason", "  Reason  ", false, " keep spaces ", null);
        when(port.patchFormDraftField(context.actor(), command)).thenReturn(new ApprovalConfigurationToolPort.FormFieldPatchResult(
                11, "fixture", "DISABLED", 4, "reason", "Reason", false, " keep spaces ", "full"));
        var args = mapper.readTree("{\"formId\":11,\"version\":3,\"fieldName\":\"reason\",\"label\":\"  Reason  \",\"required\":false,\"placeholder\":\" keep spaces \"}");
        var output = new ApprovalFormFieldPatchToolHandler(port, mapper).execute(context, args);
        try (var beans = new AnnotationConfigApplicationContext()) {
            beans.registerBean(ObjectMapper.class, () -> mapper); beans.register(AgentApprovalFormFieldPatchToolDefinitions.class); beans.refresh();
            var definition = beans.getBean(ToolDefinition.class); var validator = new ToolSchemaValidator();
            assertThat(validator.valid(definition.inputSchema(), args)).isTrue();
            assertThat(validator.valid(definition.outputSchema(), output)).isTrue();
        }
        assertThat(output.path("label").asText()).isEqualTo("Reason");
        assertThat(output.path("required").asBoolean()).isFalse();
        assertThat(output.path("placeholder").asText()).isEqualTo(" keep spaces ");
        assertThat(output.toString()).doesNotContain("schemaJson", "creatorName", "tenantId", "userId");
        verify(port).patchFormDraftField(context.actor(), command); verifyNoMoreInteractions(port);
    }
    @Test void malformedOrEmptyPatchesCannotReachTheDomainPort() throws Exception {
        var handler = new ApprovalFormFieldPatchToolHandler(port, mapper);
        for (String args : new String[]{"{}", "{\"formId\":11,\"version\":3,\"fieldName\":\"reason\"}",
                "{\"formId\":11,\"version\":0,\"fieldName\":\"reason\",\"required\":true}",
                "{\"formId\":11,\"version\":2147483647,\"fieldName\":\"reason\",\"required\":true}",
                "{\"formId\":11,\"version\":3,\"fieldName\":\"reason\",\"required\":null}",
                "{\"formId\":11,\"version\":3,\"fieldName\":\"reason\",\"label\":{}}"}) {
            assertThatThrownBy(() -> handler.execute(context, mapper.readTree(args))).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(port);
    }
}
