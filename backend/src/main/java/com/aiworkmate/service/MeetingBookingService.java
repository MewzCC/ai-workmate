package com.aiworkmate.service;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.MeetingBookingCancelRequest;
import com.aiworkmate.dto.MeetingBookingRequest;
import com.aiworkmate.dto.MeetingBookingResponse;

import java.time.LocalDateTime;

public interface MeetingBookingService {
    MeetingBookingResponse create(Long userId, MeetingBookingRequest request);

    MeetingBookingResponse createAgent(Long userId, MeetingBookingRequest request, String operationKey);

    /** Empty means not observed, not proof that an in-flight operation failed. */
    java.util.Optional<MeetingBookingResponse> findAgentCreation(
            Long userId, MeetingBookingRequest expectedRequest, String operationKey);

    PageResponse<MeetingBookingResponse> listMine(Long userId, LocalDateTime from, LocalDateTime to,
                                                   String status, int page, int size);

    PageResponse<MeetingBookingResponse> listAdmin(Long userId, Long roomId, LocalDateTime from,
                                                    LocalDateTime to, String status, int page, int size);

    MeetingBookingResponse cancel(Long userId, Long id, MeetingBookingCancelRequest request);

    MeetingBookingResponse cancelAgent(Long userId, Long id, MeetingBookingCancelRequest request,
                                       String operationKey);

    /** Empty is not evidence that an in-flight cancellation failed. */
    java.util.Optional<MeetingBookingResponse> findAgentCancellation(
            Long userId, Long id, MeetingBookingCancelRequest expectedRequest, String operationKey);
}
