package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ContractToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractRemindToolHandlerTest {
    @Test
    void mapsOneVersionedInternalReminder() throws Exception {
        ContractToolPort port = mock(ContractToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");
        var remindedAt = LocalDateTime.of(2026, 9, 30, 21, 5);
        when(port.remindContract(context.actor(), 81L, 5)).thenReturn(
                new ContractToolPort.ContractReminderResult(
                        81L, "HT-001", "EXPIRING", 2, remindedAt, 6));

        var output = new ContractRemindToolHandler(port, mapper).execute(
                context, mapper.readTree("""
                        {"contractId":81,"version":5}
                        """));

        assertThat(output.path("expiryState").asText()).isEqualTo("EXPIRING");
        assertThat(output.path("reminderCount").asInt()).isEqualTo(2);
        assertThat(mapper.treeToValue(output.path("lastRemindedAt"), LocalDateTime.class))
                .isEqualTo(remindedAt);
        assertThat(output.path("version").asInt()).isEqualTo(6);
        verify(port).remindContract(context.actor(), 81L, 5);
    }
}
