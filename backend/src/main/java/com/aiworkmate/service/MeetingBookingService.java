package com.aiworkmate.service;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.MeetingBookingCancelRequest;
import com.aiworkmate.dto.MeetingBookingRequest;
import com.aiworkmate.dto.MeetingBookingResponse;

import java.time.LocalDateTime;

public interface MeetingBookingService {
    MeetingBookingResponse create(Long userId, MeetingBookingRequest request);

    PageResponse<MeetingBookingResponse> listMine(Long userId, LocalDateTime from, LocalDateTime to,
                                                   String status, int page, int size);

    PageResponse<MeetingBookingResponse> listAdmin(Long userId, Long roomId, LocalDateTime from,
                                                    LocalDateTime to, String status, int page, int size);

    MeetingBookingResponse cancel(Long userId, Long id, MeetingBookingCancelRequest request);
}
