package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.MeetingBookingCancelRequest;
import com.aiworkmate.dto.MeetingBookingRequest;
import com.aiworkmate.dto.MeetingBookingResponse;
import com.aiworkmate.entity.MeetingBooking;
import com.aiworkmate.entity.MeetingRoom;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.MeetingBookingMapper;
import com.aiworkmate.mapper.MeetingRoomMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.MeetingBookingService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingBookingServiceImpl implements MeetingBookingService {
    private final MeetingBookingMapper bookingMapper;
    private final MeetingRoomMapper roomMapper;
    private final UserMapper userMapper;
    private final UserAccessService userAccessService;
    private final BusinessAuditService auditService;

    @Override
    @Transactional
    public MeetingBookingResponse create(Long userId, MeetingBookingRequest request) {
        return createInternal(userId, request, null);
    }

    @Override
    @Transactional
    public MeetingBookingResponse createAgent(Long userId, MeetingBookingRequest request, String operationKey) {
        if (operationKey == null || operationKey.isBlank() || operationKey.length() > 128) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return createInternal(userId, request, operationKey);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<MeetingBookingResponse> findAgentCreation(
            Long userId, MeetingBookingRequest expectedRequest, String operationKey) {
        ResolvedUserAccess actor = requirePermission(userId, "meeting:book");
        if (operationKey == null || operationKey.isBlank() || operationKey.length() > 128) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        validateRequest(expectedRequest);
        MeetingBooking booking = findAgentBooking(actor, operationKey);
        return booking == null ? java.util.Optional.empty()
                : java.util.Optional.of(replayResponse(actor, booking, expectedRequest));
    }

    private MeetingBookingResponse createInternal(
            Long userId, MeetingBookingRequest request, String operationKey) {
        ResolvedUserAccess actor = requirePermission(userId, "meeting:book");
        validateRequest(request);
        MeetingBooking existing = findAgentBooking(actor, operationKey);
        if (existing != null) return replayResponse(actor, existing, request);
        if (!request.startAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.meeting.booking.start.future");
        }
        if (!request.endAt().isAfter(request.startAt())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.meeting.booking.time.invalid");
        }
        MeetingRoom room = roomMapper.lockForBooking(actor.tenantId(), request.roomId());
        if (room == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"OPEN".equals(room.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.meeting.booking.room.closed");
        }
        if (room.getCapacity() != null && room.getCapacity() > 0
                && request.attendeeCount() > room.getCapacity()) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.meeting.booking.capacity.exceeded");
        }
        existing = findAgentBooking(actor, operationKey);
        if (existing != null) return replayResponse(actor, existing, request);
        long conflicts = bookingMapper.selectCount(new LambdaQueryWrapper<MeetingBooking>()
                .eq(MeetingBooking::getTenantId, actor.tenantId())
                .eq(MeetingBooking::getRoomId, room.getId())
                .eq(MeetingBooking::getStatus, "BOOKED")
                .lt(MeetingBooking::getStartAt, request.endAt())
                .gt(MeetingBooking::getEndAt, request.startAt()));
        if (conflicts > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.meeting.booking.conflict");
        }
        LocalDateTime now = LocalDateTime.now();
        MeetingBooking booking = new MeetingBooking();
        booking.setTenantId(actor.tenantId());
        booking.setRoomId(room.getId());
        booking.setOrganizerUserId(actor.userId());
        booking.setAgentOperationKey(operationKey);
        booking.setTitle(request.title().trim());
        booking.setAgenda(trim(request.agenda()));
        booking.setStartAt(request.startAt());
        booking.setEndAt(request.endAt());
        booking.setAttendeeCount(request.attendeeCount());
        booking.setStatus("BOOKED");
        booking.setVersion(0);
        booking.setCreatedAt(now);
        booking.setUpdatedAt(now);
        bookingMapper.insert(booking);
        auditService.recordTransactional(actor.tenantId(), actor.userId(), "MEETING_BOOKING",
                booking.getId().toString(), "CREATE", "SUCCESS",
                "roomId=" + room.getId() + ",startAt=" + booking.getStartAt()
                        + ",endAt=" + booking.getEndAt());
        return toResponse(actor, booking, room, userMapper.selectById(actor.userId()), null);
    }

    private MeetingBooking findAgentBooking(ResolvedUserAccess actor, String operationKey) {
        if (operationKey == null) return null;
        return bookingMapper.findAgentOperation(actor.tenantId(), actor.userId(), operationKey);
    }

    private MeetingBookingResponse response(ResolvedUserAccess actor, MeetingBooking booking) {
        return toResponse(actor, booking, roomMapper.selectById(booking.getRoomId()),
                userMapper.selectById(actor.userId()), null);
    }

    private MeetingBookingResponse replayResponse(ResolvedUserAccess actor, MeetingBooking booking,
                                                   MeetingBookingRequest request) {
        if (!java.util.Objects.equals(booking.getRoomId(), request.roomId())
                || !java.util.Objects.equals(booking.getTitle(), request.title().trim())
                || !java.util.Objects.equals(booking.getAgenda(), trim(request.agenda()))
                || !java.util.Objects.equals(booking.getStartAt(), request.startAt())
                || !java.util.Objects.equals(booking.getEndAt(), request.endAt())
                || !java.util.Objects.equals(booking.getAttendeeCount(), request.attendeeCount())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return response(actor, booking);
    }

    private void validateRequest(MeetingBookingRequest request) {
        if (request == null || request.roomId() == null || request.roomId() < 1
                || request.title() == null || request.title().isBlank()
                || request.title().length() > 120 || request.startAt() == null || request.endAt() == null
                || request.attendeeCount() == null || request.attendeeCount() < 1
                || request.attendeeCount() > 10000 || request.agenda() != null && request.agenda().length() > 500) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MeetingBookingResponse> listMine(Long userId, LocalDateTime from, LocalDateTime to,
                                                          String status, int page, int size) {
        ResolvedUserAccess actor = requirePermission(userId, "meeting:read:self");
        return list(actor, actor.userId(), null, from, to, status, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MeetingBookingResponse> listAdmin(Long userId, Long roomId, LocalDateTime from,
                                                           LocalDateTime to, String status, int page, int size) {
        ResolvedUserAccess actor = requirePermission(userId, "meeting:write");
        return list(actor, null, roomId, from, to, status, page, size);
    }

    @Override
    @Transactional
    public MeetingBookingResponse cancel(Long userId, Long id, MeetingBookingCancelRequest request) {
        return cancelInternal(userId, id, request, null, false);
    }

    @Override
    @Transactional
    public MeetingBookingResponse cancelAgent(
            Long userId, Long id, MeetingBookingCancelRequest request, String operationKey) {
        if (operationKey == null || operationKey.isBlank() || operationKey.length() > 128) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return cancelInternal(userId, id, request, operationKey, true);
    }

    private MeetingBookingResponse cancelInternal(
            Long userId, Long id, MeetingBookingCancelRequest request, String operationKey, boolean ownerOnly) {
        ResolvedUserAccess actor = requireAccess(userId);
        validateCancelRequest(id, request);
        if (ownerOnly && !actor.permissions().contains("meeting:cancel")) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        if (operationKey != null) {
            MeetingBooking replay = bookingMapper.findAgentCancelOperation(
                    actor.tenantId(), actor.userId(), operationKey);
            if (replay != null) return replayCancellation(actor, replay, id, request);
        }
        MeetingBooking booking = bookingMapper.selectById(id);
        if (booking == null || !actor.tenantId().equals(booking.getTenantId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        boolean admin = !ownerOnly && actor.permissions().contains("meeting:write");
        boolean owner = actor.userId().equals(booking.getOrganizerUserId())
                && actor.permissions().contains("meeting:cancel");
        if (!admin && !owner) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        LocalDateTime now = LocalDateTime.now();
        if (!"BOOKED".equals(booking.getStatus()) || !booking.getEndAt().isAfter(now)) {
            if (operationKey != null) {
                MeetingBooking replay = bookingMapper.findAgentCancelOperation(
                        actor.tenantId(), actor.userId(), operationKey);
                if (replay != null) return replayCancellation(actor, replay, id, request);
            }
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.meeting.booking.cancel.invalid");
        }
        int updated = bookingMapper.update(null, new LambdaUpdateWrapper<MeetingBooking>()
                .eq(MeetingBooking::getId, id)
                .eq(MeetingBooking::getTenantId, actor.tenantId())
                .eq(MeetingBooking::getStatus, "BOOKED")
                .eq(MeetingBooking::getVersion, request.version())
                .set(MeetingBooking::getStatus, "CANCELLED")
                .set(MeetingBooking::getCancelledByUserId, actor.userId())
                .set(MeetingBooking::getCancelledAt, now)
                .set(MeetingBooking::getCancelReason, trim(request.reason()))
                .set(operationKey != null, MeetingBooking::getAgentCancelOperationKey, operationKey)
                .set(MeetingBooking::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (updated != 1) {
            if (operationKey != null) {
                MeetingBooking replay = bookingMapper.findAgentCancelOperation(
                        actor.tenantId(), actor.userId(), operationKey);
                if (replay != null) return replayCancellation(actor, replay, id, request);
            }
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        auditService.recordTransactional(actor.tenantId(), actor.userId(), "MEETING_BOOKING",
                id.toString(), "CANCEL", "SUCCESS", "reason=" + trim(request.reason()));
        MeetingBooking cancelled = bookingMapper.selectById(id);
        MeetingRoom room = roomMapper.selectById(cancelled.getRoomId());
        return toResponse(actor, cancelled, room, userMapper.selectById(cancelled.getOrganizerUserId()),
                userMapper.selectById(actor.userId()));
    }

    private MeetingBookingResponse replayCancellation(ResolvedUserAccess actor, MeetingBooking booking,
                                                       Long expectedId, MeetingBookingCancelRequest request) {
        if (!Objects.equals(booking.getId(), expectedId)
                || !Objects.equals(booking.getTenantId(), actor.tenantId())
                || !Objects.equals(booking.getOrganizerUserId(), actor.userId())
                || !"CANCELLED".equals(booking.getStatus())
                || !Objects.equals(booking.getCancelledByUserId(), actor.userId())
                || !Objects.equals(booking.getVersion(), request.version() + 1)
                || !Objects.equals(booking.getCancelReason(), trim(request.reason()))) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return toResponse(actor, booking, roomMapper.selectById(booking.getRoomId()),
                userMapper.selectById(booking.getOrganizerUserId()), userMapper.selectById(actor.userId()));
    }

    private void validateCancelRequest(Long id, MeetingBookingCancelRequest request) {
        if (id == null || id < 1 || request == null || request.version() == null || request.version() < 0
                || request.version() == Integer.MAX_VALUE
                || request.reason() != null && request.reason().length() > 500) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
    }

    private PageResponse<MeetingBookingResponse> list(ResolvedUserAccess actor, Long organizerUserId,
                                                       Long roomId, LocalDateTime from, LocalDateTime to,
                                                       String status, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<MeetingBooking> base = bookingFilter(actor.tenantId(), organizerUserId,
                roomId, from, to, status);
        long total = bookingMapper.selectCount(base);
        LambdaQueryWrapper<MeetingBooking> query = bookingFilter(actor.tenantId(), organizerUserId,
                roomId, from, to, status)
                .orderByDesc(MeetingBooking::getStartAt)
                .last("LIMIT " + safeSize + " OFFSET " + ((safePage - 1) * safeSize));
        List<MeetingBooking> rows = bookingMapper.selectList(query);
        Map<Long, MeetingRoom> rooms = rows.isEmpty() ? Collections.emptyMap()
                : roomMapper.selectBatchIds(rows.stream().map(MeetingBooking::getRoomId).distinct().toList())
                .stream().collect(Collectors.toMap(MeetingRoom::getId, Function.identity()));
        List<Long> userIds = rows.stream()
                .flatMap(row -> java.util.stream.Stream.of(row.getOrganizerUserId(), row.getCancelledByUserId()))
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, User> users = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .filter(user -> actor.tenantId().equals(user.getTenantId()))
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.of(rows.stream().map(row -> toResponse(actor, row, rooms.get(row.getRoomId()),
                users.get(row.getOrganizerUserId()), users.get(row.getCancelledByUserId()))).toList(),
                total, safePage, safeSize);
    }

    private LambdaQueryWrapper<MeetingBooking> bookingFilter(Long tenantId, Long organizerUserId,
                                                              Long roomId, LocalDateTime from,
                                                              LocalDateTime to, String status) {
        LambdaQueryWrapper<MeetingBooking> query = new LambdaQueryWrapper<MeetingBooking>()
                .eq(MeetingBooking::getTenantId, tenantId);
        if (organizerUserId != null) query.eq(MeetingBooking::getOrganizerUserId, organizerUserId);
        if (roomId != null) query.eq(MeetingBooking::getRoomId, roomId);
        if (from != null) query.gt(MeetingBooking::getEndAt, from);
        if (to != null) query.lt(MeetingBooking::getStartAt, to);
        if (status != null && !status.isBlank()) query.eq(MeetingBooking::getStatus, status.trim());
        return query;
    }

    private MeetingBookingResponse toResponse(ResolvedUserAccess actor, MeetingBooking booking,
                                               MeetingRoom room, User organizer, User cancelledBy) {
        boolean canCancel = "BOOKED".equals(booking.getStatus())
                && booking.getEndAt().isAfter(LocalDateTime.now())
                && (actor.permissions().contains("meeting:write")
                || (actor.userId().equals(booking.getOrganizerUserId())
                && actor.permissions().contains("meeting:cancel")));
        return new MeetingBookingResponse(booking.getId(), booking.getRoomId(),
                room == null ? null : room.getCode(), room == null ? null : room.getName(),
                room == null ? null : room.getLocation(), booking.getOrganizerUserId(), userName(organizer),
                booking.getTitle(), booking.getAgenda(), booking.getStartAt(), booking.getEndAt(),
                booking.getAttendeeCount(), booking.getStatus(), booking.getVersion(),
                booking.getCancelledByUserId(), userName(cancelledBy), booking.getCancelledAt(),
                booking.getCancelReason(), booking.getCreatedAt(), booking.getUpdatedAt(), canCancel);
    }

    private ResolvedUserAccess requireAccess(Long userId) {
        ResolvedUserAccess access = userAccessService.resolveActiveUser(userId);
        if (access == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        return access;
    }

    private ResolvedUserAccess requirePermission(Long userId, String permission) {
        ResolvedUserAccess access = requireAccess(userId);
        if (!access.permissions().contains(permission)) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        return access;
    }

    private String userName(User user) {
        if (user == null) return null;
        return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername() : user.getDisplayName();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
