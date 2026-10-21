package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssetMetadataToolHandlersTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AssetToolPort port = mock(AssetToolPort.class);
    private final TrustedToolContext context = new TrustedToolContext(1, 2, 3, 4, 0, "trace");
    @Test void createUsesTrustedActorAndReturnsActualMinimalReceipt() throws Exception {
        var command = new AssetToolPort.Create(new AssetToolPort.Fields("A1", "Laptop", "IT", null, null, null, null), 9L);
        when(port.create(context.actor(), command)).thenReturn(new AssetToolPort.MetadataReceipt(5, "A1", "Laptop", "IDLE", 0));
        var result = new AssetCreateToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"assetCode\":\"A1\",\"name\":\"Laptop\",\"category\":\"IT\",\"departmentId\":9}"));
        assertThat(result.path("assetId").asLong()).isEqualTo(5);
        assertThat(result.size()).isEqualTo(5);
        verify(port, times(1)).create(context.actor(), command);
    }
    @Test void patchDistinguishesAbsentTextEmptyTextAndExplicitDateClear() throws Exception {
        var command = new AssetToolPort.Update(5, 7, new AssetToolPort.Fields(null, "Renamed", null, null, null, null, ""), true);
        when(port.update(context.actor(), command)).thenReturn(new AssetToolPort.MetadataReceipt(5, "A1", "Renamed", "IDLE", 8));
        var result = new AssetUpdateToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"assetId\":5,\"version\":7,\"name\":\"Renamed\",\"remark\":\"\",\"clearPurchaseDate\":true}"));
        assertThat(result.path("version").asInt()).isEqualTo(8);
        verify(port).update(context.actor(), command);
    }
    @Test void malformedAndAmbiguousCommandsFailBeforeDomainDispatch() throws Exception {
        var handler = new AssetUpdateToolHandler(port, mapper);
        for (String extra : new String[]{"", ",\"clearPurchaseDate\":false", ",\"originalValue\":1.001",
                ",\"purchaseDate\":\"2026-02-30\"", ",\"purchaseDate\":\"2026-01-02\",\"clearPurchaseDate\":true"}) {
            var args = mapper.readTree("{\"assetId\":5,\"version\":7" + extra + "}");
            assertThatThrownBy(() -> handler.execute(context, args)).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(port);
    }
    @Test void failureNeverBecomesSuccessOrAutomaticRetry() throws Exception {
        when(port.update(any(), any())).thenThrow(new BusinessException(ErrorCode.VERSION_CONFLICT));
        var args = mapper.readTree("{\"assetId\":5,\"version\":7,\"name\":\"X\"}");
        assertThatThrownBy(() -> new AssetUpdateToolHandler(port, mapper).execute(context, args))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("VERSION_CONFLICT");
        verify(port, times(1)).update(any(), any());
    }
}
