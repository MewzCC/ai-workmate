package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApprovalApplicationSubmitDraftToolHandlerTest {
    private final ApprovalApplicationToolPort port = mock(ApprovalApplicationToolPort.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final TrustedToolContext context = new TrustedToolContext(91L, 7L, 10L, 20L, 1, "trace");

    @Test
    void mapsOnlyOwnedResourceCoordinatesAndExpectedVersion() throws Exception {
        when(port.submitDraft(context.actor(), 30L, 2)).thenReturn(
                new ApprovalApplicationToolPort.WriteResult(30L, "expense", "PENDING", 3));

        var output = new ApprovalApplicationSubmitDraftToolHandler(port, mapper)
                .execute(context, mapper.readTree("{\"applicationId\":30,\"version\":2}"));

        assertThat(output.path("applicationId").asLong()).isEqualTo(30L);
        assertThat(output.path("status").asText()).isEqualTo("PENDING");
        assertThat(output.path("version").asInt()).isEqualTo(3);
        verify(port).submitDraft(context.actor(), 30L, 2);
    }

    @Test
    void rejectsInvalidVersionBeforeCallingPort() throws Exception {
        var handler = new ApprovalApplicationSubmitDraftToolHandler(port, mapper);

        assertThatThrownBy(() -> handler.execute(context,
                mapper.readTree("{\"applicationId\":30,\"version\":-1}")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(port);
    }
}
