package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypedWriteToolHandlerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TrustedToolContext context = new TrustedToolContext(9L, 7L, 3L, 4L, 1, "trace");

    @Test
    void fixesParseInvokeAndSerializationOrderAndOwnsStableOperationKey() throws Exception {
        List<String> calls = new ArrayList<>();
        SampleHandler handler = new SampleHandler(objectMapper, calls);

        JsonNode output = handler.execute(context, objectMapper.readTree("{\"value\":\"  draft  \"}"));

        assertThat(handler.toolCode()).isEqualTo("notification.markRead");
        assertThat(handler.handlerVersion()).isEqualTo("1.0.0");
        assertThat(calls).containsExactly("parse", "invoke:7:draft");
        assertThat(output.path("value").asText()).isEqualTo("draft");
        assertThat(output.path("operationKey").asText())
                .isEqualTo("agent:3:4:notification.markRead:v1");
        assertThat(output.has("internalValue")).isFalse();
    }

    @Test
    void rejectsIncompleteTemplateConstruction() {
        assertThatThrownBy(() -> new SampleHandler(null, new ArrayList<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
        assertThatThrownBy(() -> new SampleVersionedHandler(objectMapper, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void versionedTemplateParsesOneResourceAndOptimisticLockVersion() throws Exception {
        SampleVersionedHandler handler = new SampleVersionedHandler(objectMapper, "applicationId");

        JsonNode output = handler.execute(context,
                objectMapper.readTree("{\"applicationId\":19,\"version\":2}"));

        assertThat(output.path("value").asText()).isEqualTo("19:2:7");
    }

    @Test
    void allCurrentWriteHandlersUseTheTemplateWithoutMixingReadHandlers() {
        assertThat(List.of(
                ApprovalApplicationCreateDraftToolHandler.class,
                ApprovalApplicationSubmitDraftToolHandler.class,
                ApprovalApplicationWithdrawToolHandler.class,
                ApprovalApplicationReopenToolHandler.class,
                AttendanceReissueApplyToolHandler.class,
                LeaveApplyToolHandler.class,
                LeaveCreateDraftToolHandler.class,
                LeaveSubmitToolHandler.class,
                LeaveWithdrawToolHandler.class,
                EmployeeChangeApplyToolHandler.class,
                MeetingBookToolHandler.class,
                MeetingCancelToolHandler.class,
                NotificationMarkReadToolHandler.class
        )).allMatch(TypedWriteToolHandler.class::isAssignableFrom);
        assertThat(List.of(
                ApprovalApplicationSubmitDraftToolHandler.class,
                ApprovalApplicationWithdrawToolHandler.class,
                ApprovalApplicationReopenToolHandler.class,
                LeaveSubmitToolHandler.class,
                LeaveWithdrawToolHandler.class
        )).allMatch(TypedVersionedWriteToolHandler.class::isAssignableFrom);
        assertThat(List.of(
                AssetQueryToolHandler.class,
                TodoQueryToolHandler.class,
                NotificationMineToolHandler.class
        )).noneMatch(TypedWriteToolHandler.class::isAssignableFrom);
    }

    private static final class SampleHandler extends TypedWriteToolHandler<String, Result> {
        private final List<String> calls;

        private SampleHandler(ObjectMapper objectMapper, List<String> calls) {
            super(ToolCode.NOTIFICATION_MARK_READ, objectMapper);
            this.calls = calls;
        }

        @Override
        protected String parseArguments(JsonNode arguments) {
            calls.add("parse");
            return arguments.path("value").asText().strip();
        }

        @Override
        protected Result invoke(TrustedToolContext context, String command) {
            calls.add("invoke:" + context.userId() + ":" + command);
            return new Result(command, stableOperationKey(context), "hidden");
        }

        @Override
        protected JsonNode serializeResult(Result result) {
            return objectMapper().createObjectNode()
                    .put("value", result.value())
                    .put("operationKey", result.operationKey().value());
        }
    }

    private record Result(String value, ToolOperationKey operationKey, String internalValue) { }

    private static final class SampleVersionedHandler extends TypedVersionedWriteToolHandler<Result> {
        private SampleVersionedHandler(ObjectMapper objectMapper, String idArgument) {
            super(ToolCode.LEAVE_SUBMIT, objectMapper, idArgument);
        }

        @Override
        protected Result invokeVersioned(TrustedToolContext context, long resourceId, int version) {
            return new Result(resourceId + ":" + version + ":" + context.userId(),
                    new ToolOperationKey("unused"), null);
        }
    }
}
