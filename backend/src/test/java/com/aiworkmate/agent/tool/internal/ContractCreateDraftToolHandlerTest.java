package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ContractCreateDraftToolHandlerTest {
    @Test
    void mapsOnlyBoundedDraftFields() throws Exception {
        FinanceToolPort port = mock(FinanceToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");
        var command = new FinanceToolPort.ContractDraft(
                "HT-2027", "年度采购合同", "PURCHASE", "示例公司", null, 7L,
                new BigDecimal("100000.00"), "CNY", LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31), "年度采购");
        var updatedAt = LocalDateTime.of(2026, 9, 19, 4, 0);
        when(port.createContractDraft(eq(context.actor()), any())).thenReturn(
                new FinanceToolPort.ContractDraftResult(91L, "HT-2027", "DRAFT", 0, updatedAt));

        var output = new ContractCreateDraftToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"code":"HT-2027","name":"年度采购合同","contractType":"PURCHASE",
                 "counterpartyName":"示例公司","ownerUserId":7,"amount":100000.00,"currency":"CNY",
                 "signedDate":"2026-09-01","startDate":"2026-09-01","endDate":"2027-08-31",
                 "summary":"年度采购"}
                """));

        assertThat(output.path("contractId").asLong()).isEqualTo(91L);
        assertThat(output.path("status").asText()).isEqualTo("DRAFT");
        verify(port).createContractDraft(eq(context.actor()), argThat(actual ->
                actual.code().equals(command.code()) && actual.amount().compareTo(command.amount()) == 0
                        && actual.startDate().equals(command.startDate())
                        && actual.endDate().equals(command.endDate())));
    }

    @Test
    void rejectsInvalidDateRangeBeforeCallingPort() throws Exception {
        FinanceToolPort port = mock(FinanceToolPort.class);
        ObjectMapper mapper = new ObjectMapper();
        var handler = new ContractCreateDraftToolHandler(port, mapper);
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");

        assertThatThrownBy(() -> handler.execute(context, mapper.readTree("""
                {"code":"HT-2027","name":"年度采购合同","contractType":"PURCHASE",
                 "counterpartyName":"示例公司","ownerUserId":7,"amount":100000.00,"currency":"CNY",
                 "startDate":"2027-09-01","endDate":"2027-08-31"}
                """))).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(port);
    }
}
