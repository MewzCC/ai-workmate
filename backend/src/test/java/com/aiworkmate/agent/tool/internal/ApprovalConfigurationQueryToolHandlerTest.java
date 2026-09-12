package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalConfigurationQueryToolHandlerTest {
    private final ApprovalConfigurationToolPort port = mock(ApprovalConfigurationToolPort.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final ApprovalConfigurationQueryToolHandler handler =
            new ApprovalConfigurationQueryToolHandler(port, mapper);
    private final TrustedToolContext context = new TrustedToolContext(99L, 7L, 1L, 2L, 1, "trace");

    @Test
    void usesTrustedActorCapsPageAndOmitsExecutablePayloads() throws Exception {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 12, 9, 0);
        var query = new ApprovalConfigurationToolPort.Query(
                ApprovalConfigurationToolPort.Resource.PROCESS, "采购", "ENABLED", 2, 50);
        when(port.query(7L, query)).thenReturn(new ApprovalConfigurationToolPort.Page(List.of(
                new ApprovalConfigurationToolPort.Item(10L,
                        ApprovalConfigurationToolPort.Resource.PROCESS, "purchase", "采购审批", "采购流程",
                        "ENABLED", 2, "采购申请", null, null, updatedAt)), 1, 2, 50));

        var output = handler.execute(context, mapper.readTree(
                "{\"resource\":\"PROCESS\",\"keyword\":\"采购\",\"status\":\"ENABLED\",\"page\":2,\"size\":500}"));

        assertThat(output.at("/items/0/name").asText()).isEqualTo("采购审批");
        assertThat(output.at("/items/0/formName").asText()).isEqualTo("采购申请");
        assertThat(output.toString()).doesNotContain("nodeJson", "schemaJson", "conditionJson", "actionJson");
        verify(port).query(7L, query);
    }

    @Test
    void rejectsUnknownResourceBeforePortCall() throws Exception {
        assertThatThrownBy(() -> handler.execute(context, mapper.readTree("{\"resource\":\"UNKNOWN\"}")))
                .isInstanceOf(BusinessException.class);
        verify(port, never()).query(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }
}
