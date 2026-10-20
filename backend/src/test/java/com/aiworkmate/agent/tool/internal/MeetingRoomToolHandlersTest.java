package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.MeetingRoomToolPort;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeetingRoomToolHandlersTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MeetingRoomToolPort port = mock(MeetingRoomToolPort.class);
    private final TrustedToolContext context = new TrustedToolContext(1, 2, 3, 4, 0, "trace");
    @Test void queryReturnsActualVersionThroughTrustedActorAndBoundedPage() throws Exception {
        var query = new MeetingRoomToolPort.Query("R1", "OPEN", 2, 10);
        when(port.query(context.actor(), query)).thenReturn(new ToolPage<>(List.of(
                new MeetingRoomToolPort.Room(5, "R1", "Boardroom", null, 8, null, "OPEN", null, 7)), 11, 2, 10));
        var result = new MeetingRoomQueryToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"keyword\":\"R1\",\"status\":\"OPEN\",\"page\":2,\"size\":10}"));
        assertThat(result.path("total").asLong()).isEqualTo(11);
        assertThat(result.path("items").get(0).path("version").asInt()).isEqualTo(7);
        assertThat(result.path("items").get(0).has("location")).isFalse();
    }
    @Test void createDispatchesOnlyOneDomainCreationAndReturnsRealReceipt() throws Exception {
        var fields = new MeetingRoomToolPort.Fields("R1", "Boardroom", null, 8, null, "OPEN", null);
        when(port.create(context.actor(), fields)).thenReturn(new MeetingRoomToolPort.Receipt(5, "R1", "Boardroom", "OPEN", 0));
        var result = new MeetingRoomCreateToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"code\":\"R1\",\"name\":\"Boardroom\",\"capacity\":8,\"status\":\"OPEN\"}"));
        assertThat(result.path("roomId").asLong()).isEqualTo(5);
        verify(port, times(1)).create(context.actor(), fields);
        verifyNoMoreInteractions(port);
    }
    @Test void patchKeepsAbsentFieldsNullAndExplicitEmptyTextDistinct() throws Exception {
        var command = new MeetingRoomToolPort.Update(5, 7,
                new MeetingRoomToolPort.Fields(null, "Renamed", null, null, null, null, ""));
        when(port.update(context.actor(), command)).thenReturn(new MeetingRoomToolPort.Receipt(5, "R1", "Renamed", "OPEN", 8));
        var result = new MeetingRoomUpdateToolHandler(port, mapper).execute(context,
                mapper.readTree("{\"roomId\":5,\"version\":7,\"name\":\"Renamed\",\"remark\":\"\"}"));
        assertThat(result.path("version").asInt()).isEqualTo(8);
        verify(port).update(context.actor(), command);
    }
    @Test void invalidVersionAndNoChangesFailBeforeDispatch() throws Exception {
        var handler = new MeetingRoomUpdateToolHandler(port, mapper);
        for (String json : new String[]{"{\"roomId\":5,\"version\":7}", "{\"roomId\":5,\"version\":-1,\"name\":\"X\"}",
                "{\"roomId\":5,\"version\":7,\"status\":\"DELETED\"}"}) {
            var arguments = mapper.readTree(json);
            assertThatThrownBy(() -> handler.execute(context, arguments)).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(port);
    }
    @Test void domainFailureIsNotConvertedIntoSuccessOrRetried() throws Exception {
        when(port.update(any(), any())).thenThrow(new BusinessException(ErrorCode.VERSION_CONFLICT));
        var handler = new MeetingRoomUpdateToolHandler(port, mapper);
        var args = mapper.readTree("{\"roomId\":5,\"version\":7,\"name\":\"X\"}");
        assertThatThrownBy(() -> handler.execute(context, args)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("VERSION_CONFLICT");
        verify(port, times(1)).update(any(), any());
    }
}
