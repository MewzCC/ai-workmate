package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.PlatformObservabilityResponse;
import com.aiworkmate.dto.PlatformObservabilityTimelineResponse;
import com.aiworkmate.dto.RuntimeLogStatsResponse;
import com.aiworkmate.mapper.RuntimeLogMapper;
import com.aiworkmate.service.PlatformObservabilityService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlatformObservabilityServiceImpl implements PlatformObservabilityService {
    private static final Set<String> ALLOWED_RANGES = Set.of("24h", "7d", "30d");
    private final RuntimeLogMapper mapper;
    private final UserAccessService accessService;

    @Override
    @Transactional(readOnly = true)
    public PlatformObservabilityResponse overview(Long userId, String range) {
        ResolvedUserAccess actor = requireAccess(userId);
        String selectedRange = range == null ? "7d" : range;
        if (!ALLOWED_RANGES.contains(selectedRange)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.observability.range.invalid");
        }
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = switch (selectedRange) {
            case "24h" -> to.minusHours(24);
            case "30d" -> to.minusDays(30);
            default -> to.minusDays(7);
        };
        String interval = "24h".equals(selectedRange) ? "hour" : "day";
        Long tenantId = actor.tenantId();
        RuntimeLogStatsResponse stats = mapper.selectStats(tenantId, null, null, null, null,
                null, from, to, false);
        if (stats == null) stats = new RuntimeLogStatsResponse(0L, 0L, 0L, 0L, 0L);
        return new PlatformObservabilityResponse(from, to, interval, stats,
                mapper.selectP95Duration(tenantId, from, to),
                mapper.selectTimeline(tenantId, from, to, interval),
                mapper.selectSourceCounts(tenantId, from, to),
                mapper.selectTopErrorCodes(tenantId, from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformObservabilityTimelineResponse timeline(Long userId, LocalDateTime from,
                                                          LocalDateTime to, String interval) {
        ResolvedUserAccess actor = requireAccess(userId);
        if (from == null || to == null || from.isAfter(to)
                || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0
                || to.isAfter(LocalDateTime.now().plusMinutes(1))
                || interval == null || !Set.of("hour", "day").contains(interval)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.observability.range.invalid");
        }
        return new PlatformObservabilityTimelineResponse(from, to, interval,
                mapper.selectTimeline(actor.tenantId(), from, to, interval));
    }

    private ResolvedUserAccess requireAccess(Long userId) {
        ResolvedUserAccess actor = accessService.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        if (!actor.permissions().contains("route:platform-observability")
                || !actor.permissions().contains("runtime-log:read")) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return actor;
    }
}
