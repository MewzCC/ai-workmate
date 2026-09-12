package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetQueryToolHandlerTest {
    @Test
    void usesTrustedActorCapsPageAndReturnsSafeFields() throws Exception {
        AssetToolPort port = mock(AssetToolPort.class);
        var query = new AssetToolPort.Query("笔记本", "IT", "IN_USE", 2, 50);
        when(port.query(7L, query)).thenReturn(new AssetToolPort.Page(List.of(
                new AssetToolPort.Item(9L, "PC-009", "研发笔记本", "IT", "32GB",
                        "IN_USE", "研发部", "张三", LocalDate.of(2026, 1, 1),
                        new BigDecimal("8999.00"), "办公设备", 2, true, false, null, null)), 1, 2, 50));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var output = new AssetQueryToolHandler(port, mapper).execute(
                new TrustedToolContext(1L, 7L, 1L, 1L, 1, "trace"), mapper.readTree(
                        "{\"keyword\":\"笔记本\",\"category\":\"IT\",\"status\":\"IN_USE\",\"page\":2,\"size\":500}"));
        assertThat(output.at("/items/0/assetCode").asText()).isEqualTo("PC-009");
        assertThat(output.toString()).doesNotContain("ownerUserId", "departmentId", "tenantId", "history");
        verify(port).query(7L, query);
    }
}
