package com.aiworkmate.agent.gateway;

import com.aiworkmate.agent.registry.SideEffect;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolFailureClassifierTest {
    private final ToolFailureClassifier classifier = new ToolFailureClassifier();

    @Test
    void mapsOnlyAllowlistedBusinessFailuresWithoutUsingMessages() {
        assertClassification(ErrorCode.REQUEST_INVALID, GatewayDecision.DENY,
                GatewayDecisionCode.TOOL_INPUT_REJECTED, "DOMAIN_INPUT_REJECTED");
        assertClassification(ErrorCode.PERMISSION_DENIED, GatewayDecision.DENY,
                GatewayDecisionCode.TOOL_ACCESS_REJECTED, "DOMAIN_ACCESS_REJECTED");
        assertClassification(ErrorCode.RESOURCE_NOT_FOUND, GatewayDecision.DENY,
                GatewayDecisionCode.TOOL_RESOURCE_NOT_FOUND, "DOMAIN_RESOURCE_NOT_FOUND");
        assertClassification(ErrorCode.VERSION_CONFLICT, GatewayDecision.DENY,
                GatewayDecisionCode.TOOL_STATE_CONFLICT, "DOMAIN_STATE_CONFLICT");
        assertClassification(ErrorCode.RATE_LIMITED, GatewayDecision.THROTTLED,
                GatewayDecisionCode.GATEWAY_THROTTLED, "DOMAIN_RATE_LIMITED");

        BusinessException poisonMessage = new BusinessException(ErrorCode.VERSION_CONFLICT) {
            @Override
            public String getMessage() {
                throw new AssertionError("Classifier must not inspect exception messages");
            }
        };
        assertThat(classifier.classify(poisonMessage, SideEffect.SINGLE_WRITE).publicCode())
                .isEqualTo(GatewayDecisionCode.TOOL_STATE_CONFLICT);
    }

    @Test
    void unwrapsAsyncContainersButKeepsUnknownReadFailureUnavailable() {
        var result = classifier.classify(new ExecutionException(
                new CompletionException(new IllegalStateException("sensitive database detail"))), SideEffect.NONE);

        assertThat(result.decision()).isEqualTo(GatewayDecision.UNAVAILABLE);
        assertThat(result.publicCode()).isEqualTo(GatewayDecisionCode.GATEWAY_UNAVAILABLE);
        assertThat(result.outcomeUncertain()).isFalse();
        assertThat(result.auditCode()).isEqualTo("DOMAIN_FAILURE");
        assertThat(result.toString()).doesNotContain("sensitive", "database");
    }

    @Test
    void unknownWriteFailureAlwaysRemainsOutcomeUnknown() {
        var result = classifier.classify(
                new BusinessException(ErrorCode.SYSTEM_ERROR), SideEffect.SINGLE_WRITE);

        assertThat(result.decision()).isEqualTo(GatewayDecision.UNAVAILABLE);
        assertThat(result.publicCode()).isEqualTo(GatewayDecisionCode.TOOL_RESULT_UNKNOWN);
        assertThat(result.outcomeUncertain()).isTrue();
        assertThat(result.auditCode()).isEqualTo("DOMAIN_OUTCOME_UNKNOWN");
    }

    @Test
    void rejectsIncompleteClassificationInputs() {
        assertThatThrownBy(() -> classifier.classify(new IllegalStateException(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolFailureClassifier.Classification(
                GatewayDecision.DENY, GatewayDecisionCode.GATEWAY_DENIED, true, "INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertClassification(ErrorCode errorCode, GatewayDecision decision,
                                      GatewayDecisionCode publicCode, String auditCode) {
        var result = classifier.classify(new BusinessException(errorCode), SideEffect.SINGLE_WRITE);
        assertThat(result.decision()).isEqualTo(decision);
        assertThat(result.publicCode()).isEqualTo(publicCode);
        assertThat(result.outcomeUncertain()).isFalse();
        assertThat(result.auditCode()).isEqualTo(auditCode);
    }
}
