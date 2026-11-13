package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApprovalFormDefinitionQueryToolHandlerTest {
    private final ApprovalConfigurationToolPort port = mock(ApprovalConfigurationToolPort.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final TrustedToolContext context = new TrustedToolContext(99L, 7L, 1L, 2L, 1, "trace");
    @Test void forwardsOnlyTheTrustedActorAndRequestedIdentity() throws Exception {
        when(port.readFormDefinition(context.actor(), 11L)).thenReturn(new ApprovalConfigurationToolPort.FormDefinition(
                11L, "fixture", "Fixture", "DISABLED", 3, List.of(new ApprovalConfigurationToolPort.FormDefinitionField(
                "reason", "Reason", "text", true, null, List.of(), "full", null))));
        var output = new ApprovalFormDefinitionQueryToolHandler(port, mapper).execute(context, mapper.readTree("{\"formId\":11}"));
        assertThat(output.path("formId").asLong()).isEqualTo(11L);
        assertThat(output.at("/fields/0/name").asText()).isEqualTo("reason");
        assertThat(output.at("/fields/0").has("columns")).isFalse();
        assertThat(output.toString()).doesNotContain("schemaJson", "tenantId", "userId");
        verify(port).readFormDefinition(context.actor(), 11L);
    }
    @Test void rejectsMalformedIdentityBeforeDomainInvocation() throws Exception {
        var handler = new ApprovalFormDefinitionQueryToolHandler(port, mapper);
        for (String args : new String[]{"{}", "{\"formId\":0}", "{\"formId\":1.5}", "{\"formId\":\"11\"}"}) {
            assertThatThrownBy(() -> handler.execute(context, mapper.readTree(args))).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(port);
    }

    @Test void preservesBoundedColumnMetadataAtTheWireBoundary() throws Exception {
        var column = new ApprovalConfigurationToolPort.FormTableColumn("amount", "Exact amount", "money", true,
                List.of(), java.math.BigDecimal.ZERO);
        var field = new ApprovalConfigurationToolPort.FormDefinitionField("items", "Items", "table", true,
                null, List.of(), "full", null, List.of(column));
        when(port.readFormDefinition(context.actor(), 11L)).thenReturn(new ApprovalConfigurationToolPort.FormDefinition(
                11L, "fixture", "Fixture", "DISABLED", 3, List.of(field)));
        var output = new ApprovalFormDefinitionQueryToolHandler(port, mapper).execute(context, mapper.readTree("{\"formId\":11}"));
        assertThat(output.at("/fields/0/columns/0/name").asText()).isEqualTo("amount");
        assertThat(output.at("/fields/0/columns/0/min").decimalValue()).isEqualByComparingTo("0");
        assertThat(output.at("/fields/0").has("min")).isFalse();
    }
}
