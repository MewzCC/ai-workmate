package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.SupplierToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupplierUpdateDraftToolHandlerTest {
    @Test
    void mapsVersionedNonSensitiveFieldsOnly() throws Exception {
        SupplierToolPort port = mock(SupplierToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");
        var updatedAt = LocalDateTime.of(2026, 9, 19, 7, 0);
        when(port.updateSupplierDraft(eq(context.actor()), any())).thenReturn(
                new SupplierToolPort.SupplierDraftResult(92L, "SUP-2027", "DRAFT", 1, updatedAt));
        var output = new SupplierUpdateDraftToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"supplierId":92,"version":0,"name":"更新供应商","shortName":"更新简称",
                 "category":"SERVICE","supplierLevel":"PREFERRED","paymentTerms":"月结45天"}
                """));
        assertThat(output.path("version").asInt()).isOne();
        verify(port).updateSupplierDraft(eq(context.actor()), argThat(command ->
                command.supplierId() == 92L && command.version() == 0
                        && command.name().equals("更新供应商")
                        && command.supplierLevel().equals("PREFERRED")));
    }
}
