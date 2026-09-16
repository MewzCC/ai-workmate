package com.aiworkmate.agent.tool.port;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolWriteContractTest {
    @Test
    void operationKeyIsBoundedAndKeepsExactTrustedValue() {
        var key = new ToolOperationKey("agent:10:20:meeting.book:v1");
        assertThat(key.value()).isEqualTo("agent:10:20:meeting.book:v1");
        assertThatThrownBy(() -> new ToolOperationKey(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolOperationKey("x".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verificationDistinguishesObservedReceiptFromUnknownOutcome() {
        var receipt = new SampleReceipt(30L, "BOOKED");
        assertThat(ToolWriteVerification.observed(receipt))
                .satisfies(result -> {
                    assertThat(result.observed()).isTrue();
                    assertThat(result.receipt()).isEqualTo(receipt);
                });
        assertThat(ToolWriteVerification.<SampleReceipt>unobserved())
                .satisfies(result -> {
                    assertThat(result.observed()).isFalse();
                    assertThat(result.receipt()).isNull();
                });
    }

    @Test
    void rejectsAmbiguousVerificationStates() {
        var receipt = new SampleReceipt(30L, "BOOKED");
        assertThatThrownBy(() -> new ToolWriteVerification<>(
                ToolWriteVerification.Status.OBSERVED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolWriteVerification<>(
                ToolWriteVerification.Status.UNOBSERVED, receipt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allCurrentWriteOutputsAreTypedReceipts() {
        assertThat(List.of(
                ApprovalApplicationToolPort.WriteResult.class,
                AttendanceToolPort.ReissueWriteResult.class,
                LeaveToolPort.WriteResult.class,
                LeaveToolPort.WithdrawalResult.class,
                MeetingToolPort.WriteResult.class,
                MeetingToolPort.CancelResult.class,
                NotificationToolPort.ReadResult.class
        )).allMatch(ToolWriteReceipt.class::isAssignableFrom);
    }

    private record SampleReceipt(long resourceId, String status) implements ToolWriteReceipt { }
}
