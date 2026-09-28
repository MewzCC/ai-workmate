package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.SupplierToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupplierCreateDraftToolHandlerTest {
    @Test
    void mapsOnlyNonSensitiveDraftFields() throws Exception {
        SupplierToolPort port = mock(SupplierToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TrustedToolContext context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");
        var updatedAt = LocalDateTime.of(2026, 9, 19, 5, 0);
        when(port.createSupplierDraft(eq(context.actor()), any())).thenReturn(
                new SupplierToolPort.SupplierDraftResult(92L, "SUP-2027", "DRAFT", 0, updatedAt));

        var output = new SupplierCreateDraftToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"code":"SUP-2027","name":"示例供应商","shortName":"示例",
                 "category":"SERVICE","supplierLevel":"STANDARD","paymentTerms":"月结30天"}
                """));

        assertThat(output.path("supplierId").asLong()).isEqualTo(92L);
        verify(port).createSupplierDraft(eq(context.actor()), argThat(command ->
                command.code().equals("SUP-2027") && command.category().equals("SERVICE")
                        && command.paymentTerms().equals("月结30天")));
    }

    @Test
    void rejectsInvalidCategoryBeforeCallingPort() throws Exception {
        SupplierToolPort port = mock(SupplierToolPort.class);
        ObjectMapper mapper = new ObjectMapper();
        var handler = new SupplierCreateDraftToolHandler(port, mapper);
        var context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");
        assertThatThrownBy(() -> handler.execute(context, mapper.readTree("""
                {"code":"SUP-2027","name":"示例供应商","category":"UNBOUNDED",
                 "supplierLevel":"STANDARD"}
                """))).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(port);
    }
}
