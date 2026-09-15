package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.ApprovalApplicationResponse;
import com.aiworkmate.service.GenericApprovalService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApprovalApplicationAgentDomainToolAdapterTest {
    private final GenericApprovalService service = mock(GenericApprovalService.class);
    private final ApprovalApplicationAgentDomainToolAdapter adapter =
            new ApprovalApplicationAgentDomainToolAdapter(service);
    private final ToolActorContext actor = new ToolActorContext(10L, 20L, 30L, 40L, 1, "trace");

    @Test
    void mapsTypedFieldsToExistingDomainRequest() {
        var command = new ApprovalApplicationToolPort.Draft("expense", null, List.of(
                new ApprovalApplicationToolPort.FieldValue("reason", List.of("客户拜访"), false),
                new ApprovalApplicationToolPort.FieldValue("tags", List.of("差旅", "客户"), true)));
        var response = mock(ApprovalApplicationResponse.class);
        when(response.id()).thenReturn(30L);
        when(response.formKey()).thenReturn("expense");
        when(response.status()).thenReturn("DRAFT");
        when(response.version()).thenReturn(0);
        when(service.createAgentDraft(eq(20L), org.mockito.ArgumentMatchers.any(), eq("stable")))
                .thenReturn(response);

        var result = adapter.createDraft(actor, command, "stable");

        assertThat(result).isEqualTo(new ApprovalApplicationToolPort.WriteResult(30L, "expense", "DRAFT", 0));
        var request = ArgumentCaptor.forClass(com.aiworkmate.dto.ApprovalDraftRequest.class);
        verify(service).createAgentDraft(eq(20L), request.capture(), eq("stable"));
        assertThat(request.getValue().formData())
                .containsEntry("reason", "客户拜访")
                .containsEntry("tags", List.of("差旅", "客户"));
    }

    @Test
    void rejectsDuplicateFieldNamesWithoutCallingDomain() {
        var command = new ApprovalApplicationToolPort.Draft("expense", null, List.of(
                new ApprovalApplicationToolPort.FieldValue("reason", List.of("a"), false),
                new ApprovalApplicationToolPort.FieldValue("reason", List.of("b"), false)));

        assertThatThrownBy(() -> adapter.createDraft(actor, command, "stable"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(service);
    }
}
