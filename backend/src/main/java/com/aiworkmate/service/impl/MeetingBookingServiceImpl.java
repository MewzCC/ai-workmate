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
        ResolvedUserAccess actor = requirePermission(userId, "meeting:book");
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
        ResolvedUserAccess actor = requireAccess(userId);
        MeetingBooking booking = bookingMapper.selectById(id);
        if (booking == null || !actor.tenantId().equals(booking.getTenantId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        boolean admin = actor.permissions().contains("meeting:write");
        boolean owner = actor.userId().equals(booking.getOrganizerUserId())
                && actor.permissions().contains("meeting:cancel");
        if (!admin && !owner) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        LocalDateTime now = LocalDateTime.now();
        if (!"BOOKED".equals(booking.getStatus()) || !booking.getEndAt().isAfter(now)) {
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
                .set(MeetingBooking::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        auditService.recordTransactional(actor.tenantId(), actor.userId(), "MEETING_BOOKING",
                id.toString(), "CANCEL", "SUCCESS", "reason=" + trim(request.reason()));
        MeetingBooking cancelled = bookingMapper.selectById(id);
        MeetingRoom room = roomMapper.selectById(cancelled.getRoomId());
        return toResponse(actor, cancelled, room, userMapper.selectById(cancelled.getOrganizerUserId()),
                userMapper.selectById(actor.userId()));
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
