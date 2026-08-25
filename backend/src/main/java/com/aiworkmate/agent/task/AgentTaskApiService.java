package com.aiworkmate.agent.task;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.AgentConfirmationTokenRequest;
import com.aiworkmate.dto.AgentConfirmationTokenResponse;
import com.aiworkmate.dto.AgentTaskDetailResponse;
import com.aiworkmate.dto.AgentTaskSummaryResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AgentTaskApiService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int CONFIRMATION_RATE_PER_MINUTE = 3;

    private final AgentTaskMapper taskMapper;
    private final AgentTaskStepMapper stepMapper;
    private final AgentHashing hashing;
    private final AgentRuntimeProperties properties;
    private final AgentTaskEventService eventService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, ArrayDeque<Instant>> confirmationAttempts = new ConcurrentHashMap<>();
    private final AgentTaskStateMachine stateMachine = new AgentTaskStateMachine();

    public PageResponse<AgentTaskSummaryResponse> list(AuthenticatedUser user, String status,
                                                        LocalDateTime from, LocalDateTime to,
                                                        int page, int size) {
        String normalizedStatus = normalizeStatus(status);
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        List<AgentTaskSummaryResponse> records = taskMapper.selectOwnedPage(
                        user.tenantId(), user.userId(), normalizedStatus, from, to,
                        safeSize, (safePage - 1) * safeSize).stream()
                .map(this::summary)
                .toList();
        long total = taskMapper.countOwned(user.tenantId(), user.userId(), normalizedStatus, from, to);
        return PageResponse.of(records, total, safePage, safeSize);
    }

    public AgentTaskDetailResponse detail(AuthenticatedUser user, String taskNo) {
        AgentTask task = requireOwned(user, taskNo);
        return detail(task, stepMapper.selectByTaskId(task.getId()));
    }

    @Transactional
    public AgentTaskDetailResponse cancel(AuthenticatedUser user, String taskNo) {
        AgentTask task = requireOwned(user, taskNo);
        AgentTaskStatus status = AgentTaskStatus.valueOf(task.getStatus());
        if (!stateMachine.cancellable(status)) throw new BusinessException(ErrorCode.INVALID_TASK_STATE);
        if (taskMapper.cancelOwned(task.getId(), user.tenantId(), user.userId(), task.getStatus(), task.getVersion()) != 1) {
            throw new BusinessException(ErrorCode.INVALID_TASK_STATE);
        }
        stepMapper.cancelPending(task.getId());
        eventService.publish(task.getId(), "snapshot",
                objectMapper.createObjectNode().put("taskId", taskNo).put("status", "CANCELLED"), task.getTraceId());
        return detail(user, taskNo);
    }

    @Transactional
    public AgentConfirmationTokenResponse issueConfirmation(AuthenticatedUser user, String taskNo,
                                                             AgentConfirmationTokenRequest request) {
        if (!properties.isEnabled()) throw new BusinessException(ErrorCode.AI_TASK_CAPABILITY_UNAVAILABLE);
        AgentTask task = requireOwned(user, taskNo);
        enforceConfirmationRate(user, taskNo);
        if (!request.planVersion().equals(task.getPlanVersion()) || !request.planHash().equals(task.getPlanHash())) {
            throw new BusinessException(ErrorCode.GATEWAY_STALE);
        }
        if (!"WAITING_CONFIRMATION".equals(task.getStatus()) || "L0".equals(task.getMaxRiskLevel())) {
            throw new BusinessException(ErrorCode.INVALID_TASK_STATE);
        }

        String token = token();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(
                properties.getLimits().getConfirmationTokenTtlSeconds());
        int changed = taskMapper.issueConfirmation(task.getId(), user.tenantId(), user.userId(), task.getVersion(),
                request.planVersion(), request.planHash(), hashing.sha256(token), expiresAt);
        if (changed != 1) throw new BusinessException(ErrorCode.INVALID_TASK_STATE);
        return new AgentConfirmationTokenResponse(token,
                expiresAt.atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }

    @Transactional
    public void consumeConfirmation(AuthenticatedUser user, String taskNo, Integer planVersion,
                                    String planHash, String confirmationToken) {
        if (!properties.isEnabled() || !properties.isExecutionEnabled()) {
            throw new BusinessException(ErrorCode.AI_TASK_CAPABILITY_UNAVAILABLE);
        }
        if (!StringUtils.hasText(confirmationToken)) {
            throw new BusinessException(ErrorCode.CONFIRMATION_REQUIRED);
        }
        int changed = taskMapper.consumeConfirmation(user.tenantId(), user.userId(), taskNo,
                planVersion, planHash, hashing.sha256(confirmationToken), LocalDateTime.now().plus(
                        java.time.Duration.ofMillis(properties.getLimits().getDefaultTaskTimeoutMs())));
        if (changed != 1) throw new BusinessException(ErrorCode.CONFIRMATION_EXPIRED);
    }

    public SseEmitter events(AuthenticatedUser user, String taskNo, long lastEventId) {
        return eventService.open(user.tenantId(), user.userId(), taskNo, lastEventId);
    }

    @Scheduled(fixedDelay = 60_000L)
    public void cleanRateLimitState() {
        Instant cutoff = Instant.now().minusSeconds(60);
        confirmationAttempts.forEach((key, attempts) -> {
            synchronized (attempts) {
                while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) attempts.removeFirst();
                if (attempts.isEmpty()) confirmationAttempts.remove(key, attempts);
            }
        });
    }

    @Scheduled(fixedDelayString = "${agent.confirmation.expiry-delay-ms:5000}")
    @Transactional
    public void expireConfirmations() {
        for (AgentTask task : taskMapper.selectExpiredConfirmations(100)) {
            if (taskMapper.expireConfirmation(task.getId(), task.getVersion()) == 1) {
                eventService.publish(task.getId(), "snapshot",
                        objectMapper.createObjectNode().put("taskId", task.getTaskNo()).put("status", "EXPIRED"),
                        task.getTraceId());
            }
        }
    }

    private void enforceConfirmationRate(AuthenticatedUser user, String taskNo) {
        String key = user.tenantId() + ":" + user.userId() + ":" + taskNo;
        ArrayDeque<Instant> attempts = confirmationAttempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        Instant cutoff = Instant.now().minusSeconds(60);
        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) attempts.removeFirst();
            if (attempts.size() >= CONFIRMATION_RATE_PER_MINUTE) {
                throw new BusinessException(ErrorCode.RATE_LIMITED);
            }
            attempts.addLast(Instant.now());
        }
    }

    private AgentTask requireOwned(AuthenticatedUser user, String taskNo) {
        AgentTask task = taskMapper.selectOwned(user.tenantId(), user.userId(), taskNo);
        if (task == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return task;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) return null;
        try {
            return AgentTaskStatus.valueOf(status.trim().toUpperCase()).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
    }

    private AgentTaskSummaryResponse summary(AgentTask task) {
        return new AgentTaskSummaryResponse(task.getTaskNo(), task.getPageId(), task.getStatus(),
                task.getMaxRiskLevel(), task.getPlanVersion(), task.getCreatedAt(), task.getUpdatedAt(),
                task.getFinishedAt(), task.getErrorCode());
    }

    private AgentTaskDetailResponse detail(AgentTask task, List<AgentTaskStep> steps) {
        return new AgentTaskDetailResponse(task.getTaskNo(), task.getPageId(), task.getInput(),
                json(task.getPageContext()), json(task.getPlan()), task.getPlanHash(), task.getPlanVersion(),
                task.getMaxRiskLevel(), task.getStatus(), steps.stream().map(this::step).toList(),
                task.getTimeoutAt(), task.getCreatedAt(), task.getUpdatedAt(), task.getFinishedAt(),
                task.getErrorCode());
    }

    private AgentTaskDetailResponse.Step step(AgentTaskStep step) {
        return new AgentTaskDetailResponse.Step(step.getSequenceNo(), step.getToolCode(), step.getRiskLevel(),
                step.getStatus(), json(step.getArgs()), json(step.getResult()), step.getResultSummary(),
                step.getErrorCode(), step.getStartedAt(), step.getFinishedAt());
    }

    private JsonNode json(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored Agent JSON is invalid", exception);
        }
    }

    private String token() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
