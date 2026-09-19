package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.BudgetToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BudgetCreateDraftToolHandlerTest {
    @Test
    void mapsOnlyBoundedDraftFields() throws Exception {
        BudgetToolPort port = mock(BudgetToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");
        var command = new BudgetToolPort.BudgetDraft(
                "RD-2027", "研发预算", 2027, 7L, new BigDecimal("100000.0"),
                "CNY", 80, "年度研发");
        var updatedAt = LocalDateTime.of(2026, 9, 19, 3, 0);
        when(port.createBudgetDraft(context.actor(), command)).thenReturn(
                new BudgetToolPort.BudgetDraftResult(81L, "RD-2027", "DRAFT", 0, updatedAt));

        var output = new BudgetCreateDraftToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"code":"RD-2027","name":"研发预算","fiscalYear":2027,"ownerUserId":7,
                 "totalAmount":100000.00,"currency":"CNY","warningThreshold":80,"summary":"年度研发"}
                """));

        assertThat(output.path("budgetId").asLong()).isEqualTo(81L);
        assertThat(output.path("status").asText()).isEqualTo("DRAFT");
        verify(port).createBudgetDraft(context.actor(), command);
    }

    @Test
    void rejectsOutOfBoundaryValuesBeforeCallingPort() throws Exception {
        BudgetToolPort port = mock(BudgetToolPort.class);
        ObjectMapper mapper = new ObjectMapper();
        var handler = new BudgetCreateDraftToolHandler(port, mapper);
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");

        assertThatThrownBy(() -> handler.execute(context, mapper.readTree("""
                {"code":"RD-2027","name":"研发预算","fiscalYear":2027,"ownerUserId":7,
                 "totalAmount":0.001,"currency":"CNY","warningThreshold":80}
                """))).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(port);
    }
}
