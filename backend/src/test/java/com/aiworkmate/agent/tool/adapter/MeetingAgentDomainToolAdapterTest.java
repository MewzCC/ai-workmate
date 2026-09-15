package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.MeetingToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.MeetingBookingRequest;
import com.aiworkmate.dto.MeetingBookingResponse;
import com.aiworkmate.service.AdminAssetsService;
import com.aiworkmate.service.MeetingBookingService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeetingAgentDomainToolAdapterTest {
    private final AdminAssetsService rooms = mock(AdminAssetsService.class);
    private final MeetingBookingService bookings = mock(MeetingBookingService.class);
    private final MeetingAgentDomainToolAdapter adapter = new MeetingAgentDomainToolAdapter(rooms, bookings);
    private final ToolActorContext actor = new ToolActorContext(9, 7, 10, 20, 1, "trace");
    private final LocalDateTime start = LocalDateTime.of(2026, 10, 1, 9, 0);
    private final MeetingToolPort.BookCommand command =
            new MeetingToolPort.BookCommand(3, "Planning", "Agenda", start, start.plusHours(1), 4);

    @Test
    void mapsTypedCommandToExistingDomainServiceWithoutExtraWrites() {
        var request = new MeetingBookingRequest(3L, "Planning", "Agenda", start, start.plusHours(1), 4);
        var response = mock(MeetingBookingResponse.class);
        when(response.id()).thenReturn(12L);
        when(response.roomId()).thenReturn(3L);
        when(response.status()).thenReturn("BOOKED");
        when(response.version()).thenReturn(0);
        when(response.startAt()).thenReturn(start);
        when(response.endAt()).thenReturn(start.plusHours(1));
        when(bookings.createAgent(7L, request, "operation")).thenReturn(response);
        assertThat(adapter.book(actor, command, "operation"))
                .isEqualTo(new MeetingToolPort.WriteResult(12, 3, "BOOKED", 0, start, start.plusHours(1)));
        verify(bookings).createAgent(7L, request, "operation");
        verifyNoMoreInteractions(bookings);
        verifyNoInteractions(rooms);
    }

    @Test
    void propagatesDomainRejectionWithoutFallback() {
        var request = new MeetingBookingRequest(3L, "Planning", "Agenda", start, start.plusHours(1), 4);
        var rejection = new BusinessException(ErrorCode.PERMISSION_DENIED);
        when(bookings.createAgent(7L, request, "operation")).thenThrow(rejection);
        assertThatThrownBy(() -> adapter.book(actor, command, "operation")).isSameAs(rejection);
        verify(bookings).createAgent(7L, request, "operation");
        verifyNoMoreInteractions(bookings);
        verifyNoInteractions(rooms);
    }

    @Test
    void keepsRoomQueryBoundedAndBookingQueryPersonal() {
        when(rooms.listMeetingRooms(7L, "Floor", "OPEN", 1, 50))
                .thenReturn(PageResponse.of(List.of(), 0, 1, 50));
        when(bookings.listMine(7L, start, start.plusDays(1), "BOOKED", 2, 20))
                .thenReturn(PageResponse.of(List.of(), 0, 2, 20));
        var query = new MeetingToolPort.Query("Floor", "OPEN", start, start.plusDays(1), "BOOKED", 2, 20);
        assertThat(adapter.query(actor, query))
                .isEqualTo(new MeetingToolPort.Result(List.of(), List.of(), 0, 2, 20));
        verify(rooms).listMeetingRooms(7L, "Floor", "OPEN", 1, 50);
        verify(bookings).listMine(7L, start, start.plusDays(1), "BOOKED", 2, 20);
        verifyNoMoreInteractions(rooms, bookings);
    }

    @Test
    void unobservedCreationIsNotConvertedIntoAWrite() {
        var request = new MeetingBookingRequest(3L, "Planning", "Agenda", start, start.plusHours(1), 4);
        when(bookings.findAgentCreation(7L, request, "operation")).thenReturn(java.util.Optional.empty());
        assertThat(adapter.findCreation(actor, command, "operation")).isEmpty();
        verify(bookings).findAgentCreation(7L, request, "operation");
        verifyNoMoreInteractions(bookings);
        verifyNoInteractions(rooms);
    }
}
