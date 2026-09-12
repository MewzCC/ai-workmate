package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypedReadToolHandlerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TrustedToolContext context = new TrustedToolContext(9L, 7L, 3L, 4L, 1, "trace");

    @Test
    void fixesParseInvokeAndSanitizedSerializationOrder() throws Exception {
        List<String> calls = new ArrayList<>();
        SampleHandler handler = new SampleHandler(objectMapper, calls);

        JsonNode output = handler.execute(context, objectMapper.readTree("{\"keyword\":\"  report  \"}"));

        assertThat(handler.toolCode()).isEqualTo("todo.query");
        assertThat(handler.handlerVersion()).isEqualTo("1.0.0");
        assertThat(calls).containsExactly("parse", "invoke:7:report");
        assertThat(output.path("value").asText()).isEqualTo("report");
        assertThat(output.has("internalValue")).isFalse();
    }

    @Test
    void rejectsIncompleteTemplateConstruction() {
        assertThatThrownBy(() -> new SampleHandler(null, new ArrayList<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void allCurrentReadHandlersUseTheTemplateWithoutMixingWriteHandlers() {
        assertThat(List.of(
                ApprovalConfigurationQueryToolHandler.class,
                ApprovalTaskQueryToolHandler.class,
                AssetQueryToolHandler.class,
                EmployeeChangeQueryToolHandler.class,
                HrEmployeeQueryToolHandler.class,
                HrOrganizationQueryToolHandler.class,
                KnowledgeSearchToolHandler.class,
                LeaveMineToolHandler.class,
                MeetingQueryToolHandler.class,
                NotificationMineToolHandler.class,
                TodoQueryToolHandler.class
        )).allMatch(TypedReadToolHandler.class::isAssignableFrom);
        assertThat(List.of(
                LeaveApplyToolHandler.class,
                LeaveCreateDraftToolHandler.class,
                LeaveSubmitToolHandler.class
        )).noneMatch(TypedReadToolHandler.class::isAssignableFrom);
    }

    private static final class SampleHandler extends TypedReadToolHandler<String, Result> {
        private final List<String> calls;

        private SampleHandler(ObjectMapper objectMapper, List<String> calls) {
            super(ToolCode.TODO_QUERY, objectMapper);
            this.calls = calls;
        }

        @Override protected String parseArguments(JsonNode arguments) {
            calls.add("parse");
            return arguments.path("keyword").asText().strip();
        }

        @Override protected Result invoke(TrustedToolContext context, String query) {
            calls.add("invoke:" + context.userId() + ":" + query);
            return new Result(query, null);
        }
    }

    private record Result(String value, String internalValue) { }
}
