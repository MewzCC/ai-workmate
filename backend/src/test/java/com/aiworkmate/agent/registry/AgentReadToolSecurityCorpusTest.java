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
        Map<String, Corpus> corpora = Map.of(
                "todo.query", new Corpus(
                        definitions.todoQueryToolDefinition(objectMapper),
                        List.of("{}", "{\"status\":\"PENDING\"}", "{\"page\":2,\"size\":50}"),
                        hostileArguments("size", "51")),
                "leave.mine", new Corpus(
                        definitions.leaveMineToolDefinition(objectMapper),
                        List.of("{}", "{\"applicationId\":1}",
                                "{\"status\":\"DRAFT\",\"page\":1,\"size\":20}"),
                        hostileArguments("applicationId", "0")),
                "knowledge.search", new Corpus(
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
                                "{\"query\":\"policy\",\"context\":{\"nested\":{\"payload\":true}}}")),
                "notification.mine", new Corpus(
                        definitions.notificationMineToolDefinition(objectMapper),
                        List.of("{}", "{\"page\":1}", "{\"page\":2,\"size\":50}"),
                        hostileArguments("page", "0"))
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
