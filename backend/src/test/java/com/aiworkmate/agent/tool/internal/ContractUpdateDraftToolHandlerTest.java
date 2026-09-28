package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ContractToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContractUpdateDraftToolHandlerTest {
    @Test
    void mapsVersionedEditableFieldsOnly() throws Exception {
        ContractToolPort port = mock(ContractToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");
        var updatedAt = LocalDateTime.of(2026, 9, 19, 6, 0);
        when(port.updateContractDraft(eq(context.actor()), any())).thenReturn(
                new ContractToolPort.ContractDraftResult(81L, "HT-001", "DRAFT", 1, updatedAt));
        var output = new ContractUpdateDraftToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"contractId":81,"version":0,"name":"更新合同","contractType":"SERVICE",
                 "counterpartyName":"示例公司","ownerUserId":7,"amount":1200.00,"currency":"CNY",
                 "startDate":"2026-09-01","endDate":"2027-08-31"}
                """));
        assertThat(output.path("version").asInt()).isOne();
        verify(port).updateContractDraft(eq(context.actor()), argThat(command ->
                command.contractId() == 81L && command.version() == 0
                        && command.name().equals("更新合同")
                        && command.amount().compareTo(new java.math.BigDecimal("1200.00")) == 0));
    }
}
