package com.aiworkmate.agent.task;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.AiTaskPlanRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** Owned human-input continuation only; never executes tools or authorizes a write. */
@Service
@RequiredArgsConstructor
public class AgentClarificationService {
    public static final int MAX_ROUNDS = 3;
    public static final int TTL_MINUTES = 15;
    private final AgentTaskMapper tasks;
    private final AgentTaskEventService events;
    private final ObjectMapper json;

    public record Continuation(AgentTask source, String input, int depth) { }

    @Transactional(propagation = Propagation.MANDATORY)
    public Continuation load(AiTaskPlanRequest request, AuthenticatedUser user) {
        String reference = request.getClarificationTaskId();
        if (reference == null) return new Continuation(null, request.getInput(), 0);
        try {
            if (!UUID.fromString(reference).toString().equalsIgnoreCase(reference)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.agent.clarificationTaskId.invalid");
        }
        AgentTask source = tasks.selectOwnedForUpdate(user.tenantId(), user.userId(), reference);
        if (source == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!Objects.equals(source.getPageId(), request.getPageId())
                || !Objects.equals(source.getConversationId(), request.getConversationId()))
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        JsonNode plan = read(source.getPlan());
        JsonNode boundConversation = plan.get("clarificationConversationId");
        if ((source.getConversationId() != null || boundConversation != null)
                && (boundConversation == null || !boundConversation.isIntegralNumber() || !boundConversation.canConvertToLong()
                || !Objects.equals(source.getConversationId(), boundConversation.longValue())))
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        JsonNode depth = plan.path("clarificationDepth");
        if (!depth.isIntegralNumber() || !depth.canConvertToInt() || depth.intValue() < 0)
            throw new BusinessException(ErrorCode.INVALID_TASK_STATE);
        if (depth.intValue() >= MAX_ROUNDS)
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "agent.clarification.roundLimit");
        String input = source.getInput() + "\n\n" + request.getInput();
        if (input.getBytes(StandardCharsets.UTF_8).length > 4096)
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.agent.input.tooLong");
        return new Continuation(source, input, depth.intValue() + 1);
    }

    /** Called only after idempotent replay has returned, while the source row remains locked. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(Continuation continuation) {
        AgentTask source = continuation.source();
        if (source == null) return;
        if (!"NEEDS_INPUT".equals(source.getStatus()) || source.getConfirmationExpiresAt() == null
                || !source.getConfirmationExpiresAt().isAfter(LocalDateTime.now()))
            throw new BusinessException(ErrorCode.INVALID_TASK_STATE, "agent.clarification.unavailable");
        if (tasks.cancelOwned(source.getId(), source.getTenantId(), source.getUserId(), "NEEDS_INPUT", source.getVersion()) != 1)
            throw new BusinessException(ErrorCode.INVALID_TASK_STATE);
        events.publish(source.getId(), "snapshot", json.createObjectNode().put("taskId", source.getTaskNo())
                .put("status", "CANCELLED").put("reason", "CLARIFICATION_RESUMED"), source.getTraceId());
    }

    public JsonNode context(Continuation continuation, JsonNode requestedContext) {
        return continuation.source() == null ? requestedContext : read(continuation.source().getPageContext());
    }

    public String question(Continuation continuation) {
        JsonNode question = read(continuation.source().getPlan()).path("summary");
        if (!question.isTextual() || question.asText().isBlank() || question.asText().length() > 500)
            throw new BusinessException(ErrorCode.INVALID_TASK_STATE);
        return question.asText();
    }

    public void decorate(ObjectNode plan, Continuation continuation, boolean question) {
        if (question || continuation.source() != null) plan.put("clarificationDepth", continuation.depth());
        if (continuation.source() != null) plan.put("clarificationTaskId", continuation.source().getTaskNo());
        if (question && continuation.depth() >= MAX_ROUNDS)
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "agent.clarification.roundLimit");
    }

    private JsonNode read(String value) {
        if (value == null) throw new BusinessException(ErrorCode.INVALID_TASK_STATE);
        try {
            JsonNode parsed = json.readTree(value);
            if (parsed == null || !parsed.isObject()) throw new BusinessException(ErrorCode.INVALID_TASK_STATE);
            return parsed;
        }
        catch (JsonProcessingException invalid) { throw new BusinessException(ErrorCode.INVALID_TASK_STATE); }
    }
}
