package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentPlatformOperationsToolDefinitions {
    static final String ENDPOINT_IN = input("\"endpointId\":" + id() + ",\"keyword\":" + text(200) + ",\"status\":" + enumOf("DRAFT", "ACTIVE", "DISABLED"));
    static final String PAGE_ACTION_IN = input("\"targetPageId\":" + text(80) + ",\"enabled\":{\"type\":\"boolean\"}");
    static final String RUNTIME_IN = detailInput("\"source\":" + enumOf("INTEGRATION", "AGENT") + ",\"recordId\":" + id() + ",\"outcome\":" + enumOf("RUNNING", "SUCCEEDED", "REJECTED", "FAILED", "TIMED_OUT", "RESULT_INVALID") + ",\"keyword\":" + text(200) + ",\"from\":" + dateTime() + ",\"to\":" + dateTime(), "recordId", "source");
    static final String REPLAY_IN = input("\"replayId\":" + id() + ",\"keyword\":" + text(200) + ",\"status\":" + enumOf("RUNNING", "SUCCESS", "FAILED"));

    static final String ENDPOINT_OUT = page(item("\"id\",\"code\",\"name\",\"status\",\"version\"", "\"id\":" + id() + ",\"code\":" + text(80) + ",\"name\":" + text(200) + ",\"upstreamCode\":" + text(80) + ",\"method\":" + text(10) + ",\"relativePath\":" + text(500) + ",\"description\":" + text(1000) + ",\"status\":" + text(40) + ",\"version\":{\"type\":\"integer\"},\"updatedAt\":" + dateTime() + ",\"canManage\":{\"type\":\"boolean\"},\"canExecute\":{\"type\":\"boolean\"},\"allowedTransitions\":" + strings()));
    static final String PAGE_ACTION_OUT = page(item("\"pageId\",\"toolCode\",\"riskLevel\",\"enabled\",\"version\"", "\"pageId\":" + text(80) + ",\"toolCode\":" + text(120) + ",\"name\":" + text(200) + ",\"description\":" + text(1000) + ",\"riskLevel\":" + text(10) + ",\"sideEffect\":" + text(40) + ",\"confirmationPolicy\":" + text(40) + ",\"requiredPermissions\":" + strings() + ",\"enabled\":{\"type\":\"boolean\"},\"explicitlyConfigured\":{\"type\":\"boolean\"},\"version\":{\"type\":\"integer\"}"));
    static final String RUNTIME_OUT = page(item("\"source\",\"id\",\"outcome\"", "\"source\":" + text(20) + ",\"id\":" + id() + ",\"referenceCode\":" + text(160) + ",\"operation\":" + text(160) + ",\"outcome\":" + text(40) + ",\"decision\":" + text(80) + ",\"statusCode\":{\"type\":\"integer\"},\"durationMs\":{\"type\":\"integer\"},\"operatorLabel\":" + text(120) + ",\"errorCode\":" + text(120) + ",\"handlerInvoked\":{\"type\":\"boolean\"},\"resultBytes\":{\"type\":\"integer\"},\"attempt\":{\"type\":\"integer\"},\"startedAt\":" + dateTime() + ",\"completedAt\":" + dateTime()));
    static final String REPLAY_OUT = page(item("\"id\",\"sourceInvocationId\",\"endpointCode\",\"status\"", "\"id\":" + id() + ",\"sourceInvocationId\":" + id() + ",\"endpointCode\":" + text(80) + ",\"endpointName\":" + text(200) + ",\"method\":" + text(10) + ",\"relativePath\":" + text(500) + ",\"baselineOutcome\":" + text(40) + ",\"baselineHttpStatus\":{\"type\":\"integer\"},\"status\":" + text(40) + ",\"replayHttpStatus\":{\"type\":\"integer\"},\"replayDurationMs\":{\"type\":\"integer\"},\"replayErrorCode\":" + text(120) + ",\"comparisonResult\":" + text(40) + ",\"requestedByLabel\":" + text(120) + ",\"startedAt\":" + dateTime() + ",\"completedAt\":" + dateTime()));

    @Bean ToolDefinition integrationEndpointQueryToolDefinition(ObjectMapper m) throws JsonProcessingException { return definition(ToolCode.INTEGRATION_ENDPOINT_QUERY, "Query integration endpoints", "Returns controlled endpoint metadata without request templates or response payloads.", "integration:endpoint:read", ENDPOINT_IN, ENDPOINT_OUT, m); }
    @Bean ToolDefinition pageActionQueryToolDefinition(ObjectMapper m) throws JsonProcessingException { return definition(ToolCode.PAGE_ACTION_QUERY, "Query page actions", "Returns the code-owned page action catalog and tenant enablement state.", "page-action:read", PAGE_ACTION_IN, PAGE_ACTION_OUT, m); }
    @Bean ToolDefinition runtimeLogQueryToolDefinition(ObjectMapper m) throws JsonProcessingException { return definition(ToolCode.RUNTIME_LOG_QUERY, "Query runtime logs", "Returns bounded operational metadata without payload previews or fingerprints.", "runtime-log:read", RUNTIME_IN, RUNTIME_OUT, m); }
    @Bean ToolDefinition sandboxReplayQueryToolDefinition(ObjectMapper m) throws JsonProcessingException { return definition(ToolCode.SANDBOX_REPLAY_QUERY, "Query sandbox replays", "Returns replay outcomes without baseline or response payload content.", "integration:replay:read", REPLAY_IN, REPLAY_OUT, m); }

    private ToolDefinition definition(ToolCode code, String name, String description, String permission, String input, String output, ObjectMapper m) throws JsonProcessingException { return ToolDefinition.create(code, name, description, "Display an authorized platform operations summary.", "1.0.0", m.readTree(input), m.readTree(output), RiskLevel.L0, Set.of(permission), PermissionMode.ALL, OwnershipPolicy.TENANT_SCOPED, RetryPolicy.READ_ONLY_SAFE, SideEffect.NONE, ConfirmationPolicy.NONE, 50, 196608, 15000, "HASHED_ARGS_RESULT"); }
    private static String input(String fields) { return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{" + fields + ",\"page\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":10000},\"size\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":50}}}"; }
    private static String detailInput(String fields, String idField, String companion) { String base = input(fields); return base.substring(0, base.length() - 1) + ",\"oneOf\":[{\"required\":[\"" + idField + "\",\"" + companion + "\"]},{\"not\":{\"required\":[\"" + idField + "\"]}}]}"; }
    private static String page(String item) { return "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"items\",\"total\",\"page\",\"size\"],\"properties\":{\"items\":{\"type\":\"array\",\"maxItems\":50,\"items\":" + item + "},\"total\":{\"type\":\"integer\",\"minimum\":0},\"page\":{\"type\":\"integer\",\"minimum\":1},\"size\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":50}}}"; }
    private static String item(String required, String fields) { return "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[" + required + "],\"properties\":{" + fields + "}}"; }
    private static String text(int max) { return "{\"type\":\"string\",\"maxLength\":" + max + "}"; }
    private static String id() { return "{\"type\":\"integer\",\"minimum\":1}"; }
    private static String dateTime() { return "{\"type\":\"string\",\"format\":\"date-time\"}"; }
    private static String strings() { return "{\"type\":\"array\",\"maxItems\":50,\"items\":{\"type\":\"string\",\"maxLength\":120}}"; }
    private static String enumOf(String... values) { return "{\"type\":\"string\",\"enum\":[\"" + String.join("\",\"", values) + "\"]}"; }
}
