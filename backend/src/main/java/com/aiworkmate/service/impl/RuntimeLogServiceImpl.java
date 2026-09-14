package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.RuntimeLogDetailResponse;
import com.aiworkmate.dto.RuntimeLogPageResponse;
import com.aiworkmate.dto.RuntimeLogStatsResponse;
import com.aiworkmate.mapper.RuntimeLogMapper;
import com.aiworkmate.service.RuntimeLogService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RuntimeLogServiceImpl implements RuntimeLogService {
    private static final String ROUTE_PERMISSION = "route:runtime-logs";
    private static final String READ_PERMISSION = "runtime-log:read";
    private static final Set<String> SOURCES = Set.of("INTEGRATION", "AGENT");
    private static final Set<String> OUTCOMES = Set.of(
            "RUNNING", "SUCCEEDED", "REJECTED", "FAILED", "TIMED_OUT", "RESULT_INVALID");
    private static final Duration DEFAULT_WINDOW = Duration.ofDays(7);
    private static final Duration MAX_WINDOW = Duration.ofDays(31);

    private final RuntimeLogMapper mapper;
    private final UserAccessService accessService;

    @Override
    @Transactional(readOnly = true)
    public RuntimeLogPageResponse query(Long userId, String source, String outcome, String keyword,
                                        LocalDateTime from, LocalDateTime to, int page, int size) {
        ResolvedUserAccess actor = requireAccess(userId);
        String normalizedSource = normalizeOptional(source, SOURCES, "validation.runtimeLog.source.invalid");
        String normalizedOutcome = normalizeOptional(outcome, OUTCOMES, "validation.runtimeLog.outcome.invalid");
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        LocalDateTime effectiveTo = to == null ? LocalDateTime.now() : to;
        LocalDateTime effectiveFrom = from == null ? effectiveTo.minus(DEFAULT_WINDOW) : from;
        validateRange(effectiveFrom, effectiveTo);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        RuntimeLogStatsResponse stats = mapper.selectStats(actor.tenantId(), normalizedSource,
                normalizedOutcome, normalizedKeyword, effectiveFrom, effectiveTo);
        if (stats == null) stats = new RuntimeLogStatsResponse(0L, 0L, 0L, 0L, 0L);
        return new RuntimeLogPageResponse(
                mapper.selectPage(actor.tenantId(), normalizedSource, normalizedOutcome,
                        normalizedKeyword, effectiveFrom, effectiveTo, safeSize, (safePage - 1) * safeSize),
                stats.total() == null ? 0 : stats.total(), safePage, safeSize,
                effectiveFrom, effectiveTo, stats);
    }

    @Override
    @Transactional(readOnly = true)
    public RuntimeLogDetailResponse detail(Long userId, String source, Long id) {
        ResolvedUserAccess actor = requireAccess(userId);
        String normalizedSource = normalizeRequired(source, SOURCES, "validation.runtimeLog.source.invalid");
        RuntimeLogDetailResponse detail = mapper.selectDetail(actor.tenantId(), normalizedSource, id);
        if (detail == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return detail;
    }

    private ResolvedUserAccess requireAccess(Long userId) {
        ResolvedUserAccess actor = accessService.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        if (!actor.permissions().contains(ROUTE_PERMISSION)
                || !actor.permissions().contains(READ_PERMISSION)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return actor;
    }

    private void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.runtimeLog.range.invalid");
        }
        if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.runtimeLog.range.tooLarge");
        }
    }

    private String normalizeOptional(String value, Set<String> allowed, String messageKey) {
        return StringUtils.hasText(value) ? normalizeRequired(value, allowed, messageKey) : null;
    }

    private String normalizeRequired(String value, Set<String> allowed, String messageKey) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, messageKey);
        }
        return normalized;
    }
}
