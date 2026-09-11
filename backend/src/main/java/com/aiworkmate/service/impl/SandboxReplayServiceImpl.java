package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.TraceContext;
import com.aiworkmate.dto.SandboxReplayBaselineResponse;
import com.aiworkmate.dto.SandboxReplayDetailResponse;
import com.aiworkmate.dto.SandboxReplayPageResponse;
import com.aiworkmate.dto.SandboxReplayRecordResponse;
import com.aiworkmate.dto.SandboxReplayRequest;
import com.aiworkmate.dto.SandboxReplayStatsResponse;
import com.aiworkmate.entity.IntegrationEndpoint;
import com.aiworkmate.entity.IntegrationInvocation;
import com.aiworkmate.entity.IntegrationReplayJob;
import com.aiworkmate.mapper.IntegrationEndpointMapper;
import com.aiworkmate.mapper.IntegrationInvocationMapper;
import com.aiworkmate.mapper.IntegrationReplayJobMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.IntegrationSandboxClient;
import com.aiworkmate.service.SandboxReplayService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.aiworkmate.service.model.SandboxCallResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SandboxReplayServiceImpl implements SandboxReplayService {
    private static final String ROUTE = "route:sandbox-replay";
    private static final String READ = "integration:replay:read";
    private static final String EXECUTE = "integration:replay:execute";
    private static final Set<String> STATUSES = Set.of("RUNNING", "SUCCESS", "FAILED");
    private static final Duration EXECUTION_COOLDOWN = Duration.ofSeconds(5);

    private final IntegrationReplayJobMapper replayMapper;
    private final IntegrationInvocationMapper invocationMapper;
    private final IntegrationEndpointMapper endpointMapper;
    private final IntegrationSandboxClient sandboxClient;
    private final UserAccessService accessService;
    private final BusinessAuditService auditService;
    private final IntegrationPayloadSecurity payloadSecurity;

    @Override
    @Transactional(readOnly = true)
    public SandboxReplayPageResponse list(Long userId, String keyword, String status, int page, int size) {
        ResolvedUserAccess actor = requireRead(userId);
        String normalizedStatus = normalizeStatus(status);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String normalizedKeyword = trim(keyword);

        LambdaQueryWrapper<IntegrationReplayJob> query = base(actor.tenantId())
                .eq(normalizedStatus != null, IntegrationReplayJob::getStatus, normalizedStatus)
                .and(normalizedKeyword != null, condition -> condition
                        .like(IntegrationReplayJob::getEndpointCode, normalizedKeyword)
                        .or().like(IntegrationReplayJob::getEndpointName, normalizedKeyword)
                        .or().like(IntegrationReplayJob::getRelativePath, normalizedKeyword)
                        .or().like(IntegrationReplayJob::getTraceId, normalizedKeyword))
                .orderByDesc(IntegrationReplayJob::getStartedAt)
                .orderByDesc(IntegrationReplayJob::getId);
        Page<IntegrationReplayJob> result = replayMapper.selectPage(new Page<>(safePage, safeSize), query);

        SandboxReplayStatsResponse stats = new SandboxReplayStatsResponse(
                replayMapper.selectCount(base(actor.tenantId())),
                count(actor.tenantId(), IntegrationReplayJob::getComparisonResult, "MATCHED"),
                count(actor.tenantId(), IntegrationReplayJob::getComparisonResult, "CHANGED"),
                count(actor.tenantId(), IntegrationReplayJob::getStatus, "SUCCESS"),
                count(actor.tenantId(), IntegrationReplayJob::getStatus, "FAILED"));
        return new SandboxReplayPageResponse(
                result.getRecords().stream().map(this::recordResponse).toList(),
                result.getTotal(), safePage, safeSize, stats, canExecute(actor));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SandboxReplayBaselineResponse> baselines(Long userId, String keyword, int limit) {
        ResolvedUserAccess actor = requireRead(userId);
        return replayMapper.selectBaselines(actor.tenantId(), trim(keyword), Math.min(100, Math.max(1, limit)));
    }

    @Override
    @Transactional(readOnly = true)
    public SandboxReplayDetailResponse detail(Long userId, Long id) {
        ResolvedUserAccess actor = requireRead(userId);
        return detailResponse(requireReplay(actor, id));
    }

    @Override
    @Transactional
    public SandboxReplayDetailResponse execute(Long userId, SandboxReplayRequest request) {
        ResolvedUserAccess actor = requireExecute(userId);
        IntegrationReplayJob existing = findByIdempotency(actor, request.idempotencyKey());
        if (existing != null) {
            return reuseIdempotentResult(existing, request);
        }

        IntegrationInvocation baseline = invocationMapper.selectOne(
                new LambdaQueryWrapper<IntegrationInvocation>()
                        .eq(IntegrationInvocation::getTenantId, actor.tenantId())
                        .eq(IntegrationInvocation::getId, request.sourceInvocationId()));
        if (baseline == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        IntegrationEndpoint endpoint = endpointMapper.selectOne(
                new LambdaQueryWrapper<IntegrationEndpoint>()
                        .eq(IntegrationEndpoint::getTenantId, actor.tenantId())
                        .eq(IntegrationEndpoint::getId, baseline.getEndpointId())
                        .eq(IntegrationEndpoint::getDeleted, false));
        if (endpoint == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"ACTIVE".equals(endpoint.getStatus()) || !sandboxClient.isAvailable(endpoint.getUpstreamCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.sandboxReplay.endpoint.unavailable");
        }
        String currentHash = payloadSecurity.requestHash(
                endpoint.getHttpMethod(), endpoint.getRelativePath(), endpoint.getRequestTemplate());
        if (!Objects.equals(currentHash, baseline.getRequestHash())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.sandboxReplay.configuration.changed");
        }
        enforceCooldown(actor, baseline.getId());

        LocalDateTime now = LocalDateTime.now();
        IntegrationReplayJob reservation = reservation(actor, baseline, endpoint, request, now);
        if (replayMapper.insertReservation(reservation) != 1) {
            IntegrationReplayJob duplicate = findByIdempotency(actor, request.idempotencyKey());
            if (duplicate != null) {
                return reuseIdempotentResult(duplicate, request);
            }
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }

        SandboxCallResult call;
        try {
            call = sandboxClient.execute(endpoint.getUpstreamCode(), endpoint.getHttpMethod(),
                    endpoint.getRelativePath(), endpoint.getRequestTemplate());
        } catch (RuntimeException exception) {
            call = new SandboxCallResult("FAILED", null, 0, null, "SANDBOX_EXECUTION_ERROR");
        }
        String replayPreview = payloadSecurity.sanitizeResponsePreview(call.responsePreview());
        String replayHash = payloadSecurity.responseHash(replayPreview);
        String comparison = Objects.equals(baseline.getOutcome(), call.outcome())
                && Objects.equals(baseline.getHttpStatus(), call.httpStatus())
                && Objects.equals(reservation.getBaselineResponseHash(), replayHash)
                ? "MATCHED" : "CHANGED";
        LocalDateTime completedAt = LocalDateTime.now();

        int updated = replayMapper.update(null,
                new UpdateWrapper<IntegrationReplayJob>()
                        .eq("id", reservation.getId())
                        .eq("tenant_id", actor.tenantId())
                        .eq("status", "RUNNING")
                        .set("status", call.outcome())
                        .set("replay_http_status", call.httpStatus())
                        .set("replay_duration_ms", call.durationMs())
                        .set("replay_response_hash", replayHash)
                        .set("replay_response_preview", replayPreview)
                        .set("replay_error_code", call.errorCode())
                        .set("comparison_result", comparison)
                        .set("completed_at", completedAt));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        auditService.recordTransactional(actor.tenantId(), actor.userId(), "INTEGRATION_REPLAY",
                String.valueOf(reservation.getId()), "EXECUTE_" + call.outcome(), "SUCCESS",
                endpoint.getEndpointCode() + ":" + comparison);
        return detailResponse(requireReplay(actor, reservation.getId()));
    }

    private IntegrationReplayJob reservation(ResolvedUserAccess actor,
                                               IntegrationInvocation baseline,
                                               IntegrationEndpoint endpoint,
                                               SandboxReplayRequest request,
                                               LocalDateTime now) {
        String baselinePreview = payloadSecurity.sanitizeResponsePreview(baseline.getResponsePreview());
        IntegrationReplayJob job = new IntegrationReplayJob();
        job.setTenantId(actor.tenantId());
        job.setSourceInvocationId(baseline.getId());
        job.setEndpointId(endpoint.getId());
        job.setEndpointCode(endpoint.getEndpointCode());
        job.setEndpointName(endpoint.getName());
        job.setHttpMethod(endpoint.getHttpMethod());
        job.setRelativePath(endpoint.getRelativePath());
        job.setBaselineRequestHash(baseline.getRequestHash());
        job.setBaselineOutcome(baseline.getOutcome());
        job.setBaselineHttpStatus(baseline.getHttpStatus());
        job.setBaselineResponsePreview(baselinePreview);
        job.setBaselineResponseHash(payloadSecurity.responseHash(baselinePreview));
        job.setStatus("RUNNING");
        job.setTraceId(StringUtils.hasText(TraceContext.traceId())
                ? TraceContext.traceId() : UUID.randomUUID().toString().replace("-", ""));
        job.setReason(request.reason().trim());
        job.setIdempotencyKey(request.idempotencyKey());
        job.setRequestedBy(actor.userId());
        job.setRequestedByLabel(actor.username());
        job.setStartedAt(now);
        return job;
    }

    private void enforceCooldown(ResolvedUserAccess actor, Long sourceInvocationId) {
        IntegrationReplayJob latest = replayMapper.selectOne(base(actor.tenantId())
                .eq(IntegrationReplayJob::getSourceInvocationId, sourceInvocationId)
                .eq(IntegrationReplayJob::getRequestedBy, actor.userId())
                .orderByDesc(IntegrationReplayJob::getStartedAt)
                .last("LIMIT 1"));
        if (latest != null && latest.getStartedAt().plus(EXECUTION_COOLDOWN).isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "validation.sandboxReplay.execute.tooFrequent");
        }
    }

    private IntegrationReplayJob findByIdempotency(ResolvedUserAccess actor, String key) {
        return replayMapper.selectOne(base(actor.tenantId())
                .eq(IntegrationReplayJob::getRequestedBy, actor.userId())
                .eq(IntegrationReplayJob::getIdempotencyKey, key));
    }

    private SandboxReplayDetailResponse reuseIdempotentResult(IntegrationReplayJob existing,
                                                                SandboxReplayRequest request) {
        if (!Objects.equals(existing.getSourceInvocationId(), request.sourceInvocationId())
                || !Objects.equals(existing.getReason(), request.reason().trim())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return detailResponse(existing);
    }

    private IntegrationReplayJob requireReplay(ResolvedUserAccess actor, Long id) {
        IntegrationReplayJob job = replayMapper.selectOne(base(actor.tenantId())
                .eq(IntegrationReplayJob::getId, id));
        if (job == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return job;
    }

    private ResolvedUserAccess requireRead(Long userId) {
        ResolvedUserAccess actor = accessService.resolveActiveUser(userId);
        if (actor == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        if (!actor.permissions().contains(ROUTE) || !actor.permissions().contains(READ)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return actor;
    }

    private ResolvedUserAccess requireExecute(Long userId) {
        ResolvedUserAccess actor = requireRead(userId);
        if (!canExecute(actor)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return actor;
    }

    private boolean canExecute(ResolvedUserAccess actor) {
        return actor.permissions().contains(EXECUTE);
    }

    private LambdaQueryWrapper<IntegrationReplayJob> base(Long tenantId) {
        return new LambdaQueryWrapper<IntegrationReplayJob>()
                .eq(IntegrationReplayJob::getTenantId, tenantId);
    }

    private <T> long count(Long tenantId,
                           com.baomidou.mybatisplus.core.toolkit.support.SFunction<IntegrationReplayJob, T> column,
                           T value) {
        return replayMapper.selectCount(base(tenantId).eq(column, value));
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID,
                    "validation.sandboxReplay.status.invalid");
        }
        return normalized;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private SandboxReplayRecordResponse recordResponse(IntegrationReplayJob job) {
        return new SandboxReplayRecordResponse(job.getId(), job.getSourceInvocationId(),
                job.getEndpointCode(), job.getEndpointName(), job.getHttpMethod(), job.getRelativePath(),
                job.getBaselineOutcome(), job.getBaselineHttpStatus(), job.getStatus(),
                job.getReplayHttpStatus(), job.getReplayDurationMs(), job.getComparisonResult(),
                job.getRequestedByLabel(), job.getStartedAt(), job.getCompletedAt());
    }

    private SandboxReplayDetailResponse detailResponse(IntegrationReplayJob job) {
        return new SandboxReplayDetailResponse(job.getId(), job.getSourceInvocationId(),
                job.getEndpointCode(), job.getEndpointName(), job.getHttpMethod(), job.getRelativePath(),
                job.getBaselineRequestHash(), job.getBaselineOutcome(), job.getBaselineHttpStatus(),
                job.getBaselineResponsePreview(), job.getStatus(), job.getReplayHttpStatus(),
                job.getReplayDurationMs(), job.getReplayResponsePreview(), job.getReplayErrorCode(),
                job.getComparisonResult(), job.getTraceId(), job.getReason(), job.getRequestedByLabel(),
                job.getStartedAt(), job.getCompletedAt());
    }
}
