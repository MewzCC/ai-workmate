package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
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

        var result = adapter.createDraft(actor, command, new ToolOperationKey("stable"));

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

        assertThatThrownBy(() -> adapter.createDraft(actor, command, new ToolOperationKey("stable")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(service);
    }

    @Test
    void submitsOwnedDraftThroughExistingDomainService() {
        var response = mock(ApprovalApplicationResponse.class);
        when(response.id()).thenReturn(30L);
        when(response.formKey()).thenReturn("expense");
        when(response.status()).thenReturn("PENDING");
        when(response.version()).thenReturn(3);
        when(service.submitAgentDraft(20L, 30L, new com.aiworkmate.dto.VersionRequest(2)))
                .thenReturn(response);

        var result = adapter.submitDraft(actor, 30L, 2);

        assertThat(result).isEqualTo(
                new ApprovalApplicationToolPort.WriteResult(30L, "expense", "PENDING", 3));
        verify(service).submitAgentDraft(20L, 30L, new com.aiworkmate.dto.VersionRequest(2));
    }

    @Test
    void withdrawsOwnedPendingApplicationThroughExistingDomainService() {
        var response = mock(ApprovalApplicationResponse.class);
        when(response.id()).thenReturn(30L);
        when(response.formKey()).thenReturn("expense");
        when(response.status()).thenReturn("WITHDRAWN");
        when(response.version()).thenReturn(4);
        when(service.withdrawAgentApplication(20L, 30L, new com.aiworkmate.dto.VersionRequest(3)))
                .thenReturn(response);

        var result = adapter.withdraw(actor, 30L, 3);

        assertThat(result).isEqualTo(
                new ApprovalApplicationToolPort.WriteResult(30L, "expense", "WITHDRAWN", 4));
        verify(service).withdrawAgentApplication(20L, 30L, new com.aiworkmate.dto.VersionRequest(3));
    }

    @Test
    void reopensOwnedCompletedApplicationThroughExistingDomainService() {
        var response = mock(ApprovalApplicationResponse.class);
        when(response.id()).thenReturn(30L);
        when(response.formKey()).thenReturn("expense");
        when(response.status()).thenReturn("DRAFT");
        when(response.version()).thenReturn(5);
        when(service.reopenAgentApplication(20L, 30L, new com.aiworkmate.dto.VersionRequest(4)))
                .thenReturn(response);

        var result = adapter.reopen(actor, 30L, 4);

        assertThat(result).isEqualTo(
                new ApprovalApplicationToolPort.WriteResult(30L, "expense", "DRAFT", 5));
        verify(service).reopenAgentApplication(20L, 30L, new com.aiworkmate.dto.VersionRequest(4));
    }
}
