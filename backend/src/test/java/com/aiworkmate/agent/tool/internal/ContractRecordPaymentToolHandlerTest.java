package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ContractToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractRecordPaymentToolHandlerTest {
    @Test
    void mapsOneVersionedPayment() throws Exception {
        ContractToolPort port = mock(ContractToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");
        var updatedAt = LocalDateTime.of(2026, 9, 30, 20, 45);
        when(port.recordContractPayment(eq(context.actor()), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ContractToolPort.ContractPaymentResult(
                        81L, "HT-001", "ACTIVE", new BigDecimal("200.00"),
                        new BigDecimal("300.00"), "CNY", 5, updatedAt));

        var output = new ContractRecordPaymentToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"contractId":81,"version":4,"amount":200.00,"paymentDate":"2026-09-30",
                 "reference":"PAY-20260930-001","note":"首付款"}
                """));

        assertThat(output.path("amount").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(output.path("paidAmount").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(output.path("version").asInt()).isEqualTo(5);
        verify(port).recordContractPayment(eq(context.actor()), argThat(command ->
                command.contractId() == 81L && command.version() == 4
                        && command.amount().compareTo(new BigDecimal("200.00")) == 0
                        && command.paymentDate().equals(LocalDate.of(2026, 9, 30))
                        && command.reference().equals("PAY-20260930-001")
                        && command.note().equals("首付款")));
    }
}
