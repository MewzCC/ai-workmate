package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AgentReadToolSecurityCorpusTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolSchemaValidator validator = new ToolSchemaValidator();
    private final AgentReadToolDefinitions definitions = new AgentReadToolDefinitions();

    @TestFactory
    Stream<DynamicTest> acceptsOnlyClosedBoundedArgumentsForEveryPhase2aTool() throws Exception {
        Map<String, Corpus> corpora = Map.ofEntries(
                Map.entry("todo.query", new Corpus(
                        definitions.todoQueryToolDefinition(objectMapper),
                        List.of("{}", "{\"status\":\"PENDING\"}", "{\"page\":2,\"size\":50}"),
                        hostileArguments("size", "51"))),
                Map.entry("leave.mine", new Corpus(
                        definitions.leaveMineToolDefinition(objectMapper),
                        List.of("{}", "{\"applicationId\":1}",
                                "{\"status\":\"DRAFT\",\"page\":1,\"size\":20}"),
                        hostileArguments("applicationId", "0"))),
                Map.entry("knowledge.search", new Corpus(
                        definitions.knowledgeSearchToolDefinition(objectMapper),
                        List.of("{\"query\":\"policy\"}",
                                "{\"query\":\"policy\",\"topK\":10}",
                                "{\"query\":\"policy\",\"minScore\":0.5}"),
                        List.of(
                                "{}", "{\"query\":1}", "{\"query\":\"\"}",
                                "{\"query\":\"policy\",\"userId\":7}",
                                "{\"query\":\"policy\",\"tenantId\":99}",
                                "{\"query\":\"https://attacker.invalid\",\"url\":\"https://attacker.invalid\"}",
                                "{\"query\":\"SELECT * FROM users\",\"sql\":\"DROP TABLE users\"}",
                                "{\"query\":\"policy\",\"topK\":11}",
                                "{\"query\":\"policy\",\"context\":{\"nested\":{\"payload\":true}}}"))),
                Map.entry("notification.mine", new Corpus(
                        definitions.notificationMineToolDefinition(objectMapper),
                        List.of("{}", "{\"page\":1}", "{\"page\":2,\"size\":50}"),
                        hostileArguments("page", "0"))),
                Map.entry("approval.configuration.query", new Corpus(
                        definitions.approvalConfigurationQueryToolDefinition(objectMapper),
                        List.of("{\"resource\":\"FORM\"}",
                                "{\"resource\":\"PROCESS\",\"keyword\":\"采购\"}",
                                "{\"resource\":\"RULE\",\"page\":2,\"size\":50}"),
                        List.of("{}", "{\"resource\":\"UNKNOWN\"}",
                                "{\"resource\":\"FORM\",\"userId\":7}",
                                "{\"resource\":\"FORM\",\"size\":51}",
                                "{\"resource\":\"FORM\",\"url\":\"https://attacker.invalid\"}"))),
                Map.entry("approval.task.query", new Corpus(
                        definitions.approvalTaskQueryToolDefinition(objectMapper),
                        List.of("{}", "{\"status\":\"PENDING\",\"keyword\":\"采购\"}",
                                "{\"from\":\"2026-09-01T00:00:00\",\"to\":\"2026-09-30T23:59:00\",\"size\":50}"),
                        List.of("{\"status\":\"UNKNOWN\"}", "{\"userId\":7}",
                                "{\"tenantId\":99}", "{\"size\":51}",
                                "{\"sql\":\"SELECT * FROM leave_application\"}"))),
                Map.entry("hr.organization.query", new Corpus(
                        definitions.hrOrganizationQueryToolDefinition(objectMapper),
                        List.of("{}", "{\"keyword\":\"研发\"}", "{\"limit\":50}"),
                        hostileArguments("limit", "51"))),
                Map.entry("hr.employee.query", new Corpus(
                        definitions.hrEmployeeQueryToolDefinition(objectMapper),
                        List.of("{\"employeeId\":1}", "{\"employeeId\":999}"),
                        List.of("{}", "{\"employeeId\":0}", "{\"employeeId\":1,\"tenantId\":2}"))),
                Map.entry("hr.change.query", new Corpus(
                        definitions.employeeChangeQueryToolDefinition(objectMapper),
                        List.of("{}", "{\"status\":\"PENDING\"}",
                                "{\"changeType\":\"TRANSFER\",\"page\":2,\"size\":50}"),
                        List.of("{\"changeType\":\"DELETE\"}", "{\"size\":51}",
                                "{\"tenantId\":2}", "{\"sql\":\"select * from employee_change\"}"))),
                Map.entry("asset.query", new Corpus(
                        definitions.assetQueryToolDefinition(objectMapper),
                        List.of("{}", "{\"status\":\"IDLE\"}",
                                "{\"category\":\"IT\",\"keyword\":\"笔记本\",\"page\":2,\"size\":50}"),
                        List.of("{\"status\":\"DELETED\"}", "{\"size\":51}",
                                "{\"tenantId\":2}", "{\"sql\":\"select * from asset_ledger\"}"))),
                Map.entry("meeting.query", new Corpus(
                        definitions.meetingQueryToolDefinition(objectMapper),
                        List.of("{}", "{\"roomStatus\":\"OPEN\"}",
                                "{\"from\":\"2026-09-13T09:00:00\",\"to\":\"2026-09-14T09:00:00\",\"bookingStatus\":\"BOOKED\",\"size\":50}"),
                        List.of("{\"roomStatus\":\"DELETED\"}", "{\"bookingStatus\":\"FINISHED\"}",
                                "{\"size\":51}", "{\"tenantId\":2}", "{\"url\":\"https://attacker.invalid\"}"))),
                Map.entry("attendance.query", new Corpus(
                        definitions.attendanceQueryToolDefinition(objectMapper),
                        List.of("{\"resource\":\"TODAY\"}",
                                "{\"resource\":\"EXCEPTIONS\",\"from\":\"2026-09-01\",\"to\":\"2026-09-30\",\"size\":50}",
                                "{\"resource\":\"STATISTICS\",\"year\":2026,\"month\":9}"),
                        List.of("{}", "{\"resource\":\"CLOCK\"}", "{\"resource\":\"RECORDS\",\"userId\":7}",
                                "{\"resource\":\"RECORDS\",\"size\":51}",
                                "{\"resource\":\"STATISTICS\",\"month\":13}")))
        );

        return corpora.entrySet().stream().flatMap(entry -> {
            String toolCode = entry.getKey();
            Corpus corpus = entry.getValue();
            Stream<DynamicTest> accepted = corpus.accepted().stream().map(json -> DynamicTest.dynamicTest(
                    toolCode + " accepts " + json,
                    () -> assertThat(valid(corpus.definition(), json)).isTrue()));
            Stream<DynamicTest> rejected = corpus.rejected().stream().map(json -> DynamicTest.dynamicTest(
                    toolCode + " rejects " + json,
                    () -> assertThat(valid(corpus.definition(), json)).isFalse()));
            return Stream.concat(accepted, rejected);
        });
    }

    private List<String> hostileArguments(String boundedField, String invalidValue) {
        return List.of(
                "{\"userId\":7}",
                "{\"tenantId\":99}",
                "{\"page\":\"1\"}",
                "{\"filter\":{\"nested\":{\"tenantId\":99}}}",
                "{\"url\":\"https://attacker.invalid\"}",
                "{\"sql\":\"SELECT * FROM users\"}",
                "{\"%s\":%s}".formatted(boundedField, invalidValue),
                "{\"context\":\"%s\"}".formatted("x".repeat(4097))
        );
    }

    private boolean valid(ToolDefinition definition, String json) throws Exception {
        JsonNode arguments = objectMapper.readTree(json);
        return validator.valid(definition.inputSchema(), arguments);
    }

    private record Corpus(ToolDefinition definition, List<String> accepted, List<String> rejected) {
    }
}
