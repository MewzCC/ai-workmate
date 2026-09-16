package com.aiworkmate.agent.gateway;

import com.aiworkmate.agent.registry.SideEffect;
import com.aiworkmate.common.BusinessException;

import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Code-owned, message-free classification for failures raised after a handler was invoked. */
public final class ToolFailureClassifier {
    private static final Set<String> INPUT_REJECTIONS = Set.of(
            "REQUEST_INVALID", "SCHEMA_INVALID");
    private static final Set<String> ACCESS_REJECTIONS = Set.of(
            "AUTH_REQUIRED", "AUTH_TOKEN_INVALID", "AUTH_TOKEN_EXPIRED", "AUTH_ACCOUNT_LOCKED",
            "PERMISSION_DENIED", "RESOURCE_FORBIDDEN");
    private static final Set<String> NOT_FOUND_REJECTIONS = Set.of(
            "RESOURCE_NOT_FOUND", "ATTENDANCE_RECORD_NOT_FOUND", "ATTENDANCE_REISSUE_NOT_FOUND");
    private static final Set<String> STATE_REJECTIONS = Set.of(
            "APPROVER_NOT_CONFIGURED", "BUSINESS_STATE_INVALID", "VERSION_CONFLICT",
            "IDEMPOTENCY_CONFLICT", "ATTENDANCE_ALREADY_CLOCKED", "ATTENDANCE_APPROVER_MISSING",
            "ATTENDANCE_REISSUE_DECIDED");

    public Classification classify(Throwable failure, SideEffect sideEffect) {
        if (sideEffect == null) {
            throw new IllegalArgumentException("Tool side effect is required");
        }
        Throwable cause = unwrap(failure);
        if (cause instanceof BusinessException businessException) {
            String errorCode = businessException.getErrorCode();
            if (INPUT_REJECTIONS.contains(errorCode)) {
                return rejected(GatewayDecisionCode.TOOL_INPUT_REJECTED, "DOMAIN_INPUT_REJECTED");
            }
            if (ACCESS_REJECTIONS.contains(errorCode)) {
                return rejected(GatewayDecisionCode.TOOL_ACCESS_REJECTED, "DOMAIN_ACCESS_REJECTED");
            }
            if (NOT_FOUND_REJECTIONS.contains(errorCode)) {
                return rejected(GatewayDecisionCode.TOOL_RESOURCE_NOT_FOUND, "DOMAIN_RESOURCE_NOT_FOUND");
            }
            if (STATE_REJECTIONS.contains(errorCode)) {
                return rejected(GatewayDecisionCode.TOOL_STATE_CONFLICT, "DOMAIN_STATE_CONFLICT");
            }
            if ("RATE_LIMITED".equals(errorCode)) {
                return new Classification(GatewayDecision.THROTTLED,
                        GatewayDecisionCode.GATEWAY_THROTTLED, false, "DOMAIN_RATE_LIMITED");
            }
        }
        return sideEffect == SideEffect.NONE
                ? new Classification(GatewayDecision.UNAVAILABLE,
                        GatewayDecisionCode.GATEWAY_UNAVAILABLE, false, "DOMAIN_FAILURE")
                : new Classification(GatewayDecision.UNAVAILABLE,
                        GatewayDecisionCode.TOOL_RESULT_UNKNOWN, true, "DOMAIN_OUTCOME_UNKNOWN");
    }

    private Classification rejected(GatewayDecisionCode code, String auditCode) {
        return new Classification(GatewayDecision.DENY, code, false, auditCode);
    }

    private Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public record Classification(
            GatewayDecision decision,
            GatewayDecisionCode publicCode,
            boolean outcomeUncertain,
            String auditCode
    ) {
        public Classification {
            if (decision == null || publicCode == null || auditCode == null || auditCode.isBlank()) {
                throw new IllegalArgumentException("Complete tool failure classification is required");
            }
            if (outcomeUncertain && publicCode != GatewayDecisionCode.TOOL_RESULT_UNKNOWN) {
                throw new IllegalArgumentException("Uncertain outcomes require the stable unknown-result code");
            }
        }

        ToolGatewayResult result() {
            return new ToolGatewayResult(decision, publicCode, null, outcomeUncertain);
        }
    }
}
