package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentSecurityGovernanceToolDefinitions {
    static final String ACCESS_IN = object("\"filterCode\":" + text(80));
    static final String DATA_IN = object("\"scopeType\":" + enumOf("SELF", "DEPARTMENT", "DEPARTMENT_TREE", "CUSTOM_DEPARTMENTS", "ALL") + ",\"enabled\":{\"type\":\"boolean\"}");
    static final String AI_IN = object("\"toolCode\":" + text(120) + ",\"filterCode\":" + text(80) + ",\"effectiveEnabled\":{\"type\":\"boolean\"}");
    static final String ACCESS_OUT = object("\"roles\":" + array(item("\"code\",\"name\",\"builtin\",\"permissionCount\"", "\"code\":" + text(80) + ",\"name\":" + text(160) + ",\"description\":" + text(500) + ",\"builtin\":{\"type\":\"boolean\"},\"permissionCount\":" + count())) + ",\"userCount\":" + count() + ",\"permissionCount\":" + count() + ",\"routeCount\":" + count() + ",\"departmentCount\":" + count() + ",\"positionCount\":" + count(), "\"roles\",\"userCount\",\"permissionCount\",\"routeCount\",\"departmentCount\",\"positionCount\"");
    static final String DATA_OUT = object("\"policies\":" + array(item("\"id\",\"name\",\"scopeType\",\"enabled\",\"version\"", "\"id\":" + id() + ",\"name\":" + text(160) + ",\"description\":" + text(500) + ",\"scopeType\":" + text(40) + ",\"departmentCount\":" + count() + ",\"enabled\":{\"type\":\"boolean\"},\"version\":" + count() + ",\"updatedAt\":" + dateTime())) + ",\"roleBindingCount\":" + count() + ",\"userExceptionCount\":" + count(), "\"policies\",\"roleBindingCount\",\"userExceptionCount\"");
    static final String AI_OUT = object("\"runtime\":" + switches("agentEnabled,planningEnabled,executionEnabled,writeToolsEnabled") + ",\"tenant\":" + switches("enabled,writeToolsEnabled") + ",\"tools\":" + array(item("\"code\",\"name\",\"riskLevel\",\"effectiveEnabled\"", "\"code\":" + text(120) + ",\"name\":" + text(160) + ",\"riskLevel\":" + text(8) + ",\"sideEffect\":" + text(30) + ",\"confirmationPolicy\":" + text(30) + ",\"pageIds\":" + stringArray() + ",\"platformEnabled\":{\"type\":\"boolean\"},\"tenantEnabled\":{\"type\":\"boolean\"},\"effectiveEnabled\":{\"type\":\"boolean\"}")) + ",\"roles\":" + array(item("\"code\",\"name\",\"builtin\",\"immutable\",\"eligibleToolCount\",\"grantedToolCount\"", "\"code\":" + text(80) + ",\"name\":" + text(160) + ",\"builtin\":{\"type\":\"boolean\"},\"immutable\":{\"type\":\"boolean\"},\"eligibleToolCount\":" + count() + ",\"grantedToolCount\":" + count())), "\"runtime\",\"tenant\",\"tools\",\"roles\"");

    @Bean ToolDefinition accessGovernanceQueryToolDefinition(ObjectMapper m) throws JsonProcessingException { return definition(ToolCode.ACCESS_GOVERNANCE_QUERY, "Query access governance", "Returns role and access catalog counts without user identities.", "access:manage", ACCESS_IN, ACCESS_OUT, m); }
    @Bean ToolDefinition dataPermissionQueryToolDefinition(ObjectMapper m) throws JsonProcessingException { return definition(ToolCode.DATA_PERMISSION_QUERY, "Query data permissions", "Returns tenant data-scope policy summaries without user or department identifiers.", "data-scope:manage", DATA_IN, DATA_OUT, m); }
    @Bean ToolDefinition aiPermissionQueryToolDefinition(ObjectMapper m) throws JsonProcessingException { return definition(ToolCode.AI_PERMISSION_QUERY, "Query Agent permissions", "Returns effective Agent switches and grant counts without internal policy rows.", "agent-permission:manage", AI_IN, AI_OUT, m); }
    private ToolDefinition definition(ToolCode code, String name, String description, String permission, String input, String output, ObjectMapper m) throws JsonProcessingException { return ToolDefinition.create(code, name, description, "Display an authorized governance summary.", "1.0.0", m.readTree(input), m.readTree(output), RiskLevel.L0, Set.of(permission), PermissionMode.ALL, OwnershipPolicy.TENANT_SCOPED, RetryPolicy.READ_ONLY_SAFE, SideEffect.NONE, ConfirmationPolicy.NONE, 50, 196608, 15000, "HASHED_ARGS_RESULT"); }
    private static String object(String fields) { return object(fields, null); }
    private static String object(String fields, String required) { return "{\"type\":\"object\",\"additionalProperties\":false," + (required == null ? "" : "\"required\":[" + required + "],") + "\"properties\":{" + fields + "}}"; }
    private static String item(String required, String fields) { return object(fields, required); }
    private static String array(String item) { return "{\"type\":\"array\",\"maxItems\":50,\"items\":" + item + "}"; }
    private static String text(int max) { return "{\"type\":\"string\",\"maxLength\":" + max + "}"; }
    private static String id() { return "{\"type\":\"integer\",\"minimum\":1}"; }
    private static String count() { return "{\"type\":\"integer\",\"minimum\":0}"; }
    private static String dateTime() { return "{\"type\":\"string\",\"format\":\"date-time\"}"; }
    private static String stringArray() { return "{\"type\":\"array\",\"maxItems\":50,\"items\":" + text(80) + "}"; }
    private static String enumOf(String... values) { return "{\"type\":\"string\",\"enum\":[\"" + String.join("\",\"", values) + "\"]}"; }
    private static String switches(String csv) { String[] names = csv.split(","); StringBuilder fields = new StringBuilder(); StringBuilder required = new StringBuilder(); for (String name : names) { if (!fields.isEmpty()) { fields.append(','); required.append(','); } fields.append('"').append(name).append("\":{\"type\":\"boolean\"}"); required.append('"').append(name).append('"'); } return object(fields.toString(), required.toString()); }
}
