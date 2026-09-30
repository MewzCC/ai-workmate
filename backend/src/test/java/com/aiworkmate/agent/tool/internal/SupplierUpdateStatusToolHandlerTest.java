package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.SupplierToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierUpdateStatusToolHandlerTest {
    @Test
    void mapsOneVersionedStatusTransition() throws Exception {
        SupplierToolPort port = mock(SupplierToolPort.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");
        var updatedAt = LocalDateTime.of(2026, 9, 30, 19, 55);
        when(port.updateSupplierStatus(eq(context.actor()), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SupplierToolPort.SupplierDraftResult(
                        92L, "SUP-2027", "SUSPENDED", 2, updatedAt));

        var output = new SupplierUpdateStatusToolHandler(port, mapper).execute(context, mapper.readTree("""
                {"supplierId":92,"version":1,"status":"SUSPENDED","reason":"暂停合作"}
                """));

        assertThat(output.path("status").asText()).isEqualTo("SUSPENDED");
        assertThat(output.path("version").asInt()).isEqualTo(2);
        verify(port).updateSupplierStatus(eq(context.actor()), argThat(command ->
                command.supplierId() == 92L && command.version() == 1
                        && command.status().equals("SUSPENDED")
                        && command.reason().equals("暂停合作")));
    }
}
