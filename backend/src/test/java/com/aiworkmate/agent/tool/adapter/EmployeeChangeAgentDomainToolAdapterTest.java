package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.EmployeeChangeToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.agent.tool.port.ToolWriteVerification;
import com.aiworkmate.service.EmployeeChangeService;
import com.aiworkmate.service.model.EmployeeChangeAgentApplicationReceipt;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeChangeAgentDomainToolAdapterTest {
    private final EmployeeChangeService service = mock(EmployeeChangeService.class);
    private final EmployeeChangeAgentDomainToolAdapter adapter =
            new EmployeeChangeAgentDomainToolAdapter(service);
    private final ToolActorContext context =
            new ToolActorContext(91L, 7L, 10L, 20L, 1, "trace");
    private final ToolOperationKey key = new ToolOperationKey("operation-1");
    private final EmployeeChangeToolPort.ApplicationCommand command =
            new EmployeeChangeToolPort.ApplicationCommand(
                    31L, "TRANSFER", LocalDate.of(2026, 9, 30),
                    4L, 5L, null, 12L, "团队调整");

    @Test
    void mapsTrustedActorAndDomainReceipt() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 9, 18, 11, 30);
        when(service.createAgent(org.mockito.ArgumentMatchers.eq(7L), any(),
                org.mockito.ArgumentMatchers.eq("operation-1"))).thenReturn(
                new EmployeeChangeAgentApplicationReceipt(41L, "PENDING", 0, submittedAt));

        assertThat(adapter.apply(context, command, key)).isEqualTo(
                new EmployeeChangeToolPort.ApplicationResult(41L, "PENDING", 0, submittedAt));
        verify(service).createAgent(org.mockito.ArgumentMatchers.eq(7L), any(),
                org.mockito.ArgumentMatchers.eq("operation-1"));
    }

    @Test
    void exposesReadOnlyOutcomeVerification() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 9, 18, 11, 30);
        when(service.findAgentApplication(org.mockito.ArgumentMatchers.eq(7L), any(),
                org.mockito.ArgumentMatchers.eq("operation-1"))).thenReturn(Optional.of(
                new EmployeeChangeAgentApplicationReceipt(41L, "PENDING", 0, submittedAt)));

        assertThat(adapter.findApplication(context, command, key)).isEqualTo(
                ToolWriteVerification.observed(new EmployeeChangeToolPort.ApplicationResult(
                        41L, "PENDING", 0, submittedAt)));
    }
}
