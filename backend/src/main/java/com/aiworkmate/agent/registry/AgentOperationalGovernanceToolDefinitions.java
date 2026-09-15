package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentOperationalGovernanceToolDefinitions {
    static final String EMPTY_IN = object("");
    static final String AUDIT_IN = object("\"action\":" + text(100) + ",\"resourceType\":" + text(100) + ",\"result\":" + text(40) + ",\"from\":" + dateTime() + ",\"to\":" + dateTime() + ",\"page\":" + positive(10000) + ",\"size\":" + positive(50));
    static final String DICTIONARY_IN = object("\"keyword\":" + text(100) + ",\"status\":" + enumOf("ACTIVE", "DISABLED"));
    static final String AUDIT_OUT = page(item("\"id\",\"resourceType\",\"action\",\"result\",\"createdAt\"", "\"id\":" + id() + ",\"resourceType\":" + text(100) + ",\"action\":" + text(100) + ",\"result\":" + text(40) + ",\"createdAt\":" + dateTime()));
    static final String TENANT_OUT = object("\"tenantName\":" + text(160) + ",\"tenantShortName\":" + text(80) + ",\"locale\":" + text(20) + ",\"timezone\":" + text(80) + ",\"fiscalYearStartMonth\":" + positive(12) + ",\"features\":" + switches("approval,attendance,asset,meeting,visitor,seal") + ",\"defaultApprovalDays\":" + positive(365) + ",\"expenseCurrency\":" + text(10) + ",\"passwordMinLength\":" + positive(128) + ",\"sessionTimeoutMinutes\":" + positive(10080) + ",\"version\":" + count() + ",\"updatedAt\":" + dateTime(), "\"tenantName\",\"locale\",\"timezone\",\"fiscalYearStartMonth\",\"features\",\"defaultApprovalDays\",\"expenseCurrency\",\"passwordMinLength\",\"sessionTimeoutMinutes\",\"version\",\"updatedAt\"");
    static final String DICTIONARY_OUT = object("\"records\":" + array(item("\"code\",\"name\",\"status\",\"sortOrder\",\"itemCount\",\"activeItemCount\",\"version\",\"updatedAt\"", "\"code\":" + text(80) + ",\"name\":" + text(160) + ",\"description\":" + text(500) + ",\"status\":" + text(40) + ",\"sortOrder\":" + count() + ",\"itemCount\":" + count() + ",\"activeItemCount\":" + count() + ",\"version\":" + count() + ",\"updatedAt\":" + dateTime())) + ",\"canManage\":{\"type\":\"boolean\"}", "\"records\",\"canManage\"");
    static final String SYSTEM_OUT = object("\"checkedAt\":" + dateTime() + ",\"capabilities\":" + array(item("\"code\",\"enabled\",\"available\",\"status\"", "\"code\":" + text(40) + ",\"enabled\":{\"type\":\"boolean\"},\"available\":{\"type\":\"boolean\"},\"status\":" + text(40))), "\"checkedAt\",\"capabilities\"");

    @Bean ToolDefinition auditQueryToolDefinition(ObjectMapper m) throws JsonProcessingException { return definition(ToolCode.AUDIT_QUERY, "Query audit records", "Returns tenant audit metadata without actor identifiers, resource identifiers, traces or summaries.", "audit:read", AUDIT_IN, AUDIT_OUT, m); }
    @Bean ToolDefinition tenantConfigurationQueryToolDefinition(ObjectMapper m) throws JsonProcessingException { return definition(ToolCode.TENANT_CONFIGURATION_QUERY, "Query tenant configuration", "Returns the authorized tenant business and security policy summary.", "tenant:config:manage", EMPTY_IN, TENANT_OUT, m); }
    @Bean ToolDefinition dictionaryQueryToolDefinition(ObjectMapper m) throws JsonProcessingException { return definition(ToolCode.DICTIONARY_QUERY, "Query dictionaries", "Returns dictionary type summaries without internal identifiers or item values.", "dictionary:manage", DICTIONARY_IN, DICTIONARY_OUT, m); }
    @Bean ToolDefinition systemCapabilityQueryToolDefinition(ObjectMapper m) throws JsonProcessingException { return definition(ToolCode.SYSTEM_CAPABILITY_QUERY, "Query system capabilities", "Returns availability flags without credentials, endpoints, connection strings or exception details.", "access:manage", EMPTY_IN, SYSTEM_OUT, m); }
    private ToolDefinition definition(ToolCode code, String name, String description, String permission, String input, String output, ObjectMapper m) throws JsonProcessingException { return ToolDefinition.create(code, name, description, "Display an authorized operational governance summary.", "1.0.0", m.readTree(input), m.readTree(output), RiskLevel.L0, Set.of(permission), PermissionMode.ALL, OwnershipPolicy.TENANT_SCOPED, RetryPolicy.READ_ONLY_SAFE, SideEffect.NONE, ConfirmationPolicy.NONE, 50, 196608, 15000, "HASHED_ARGS_RESULT"); }
    private static String object(String fields) { return object(fields, null); }
    private static String object(String fields, String required) { return "{\"type\":\"object\",\"additionalProperties\":false," + (required == null ? "" : "\"required\":[" + required + "],") + "\"properties\":{" + fields + "}}"; }
    private static String item(String required, String fields) { return object(fields, required); }
    private static String page(String row) { return object("\"records\":" + array(row) + ",\"total\":" + count() + ",\"page\":" + positive(10000) + ",\"size\":" + positive(50), "\"records\",\"total\",\"page\",\"size\""); }
    private static String array(String item) { return "{\"type\":\"array\",\"maxItems\":50,\"items\":" + item + "}"; }
    private static String text(int max) { return "{\"type\":\"string\",\"maxLength\":" + max + "}"; }
    private static String id() { return "{\"type\":\"integer\",\"minimum\":1}"; }
    private static String count() { return "{\"type\":\"integer\",\"minimum\":0}"; }
    private static String positive(int max) { return "{\"type\":\"integer\",\"minimum\":1,\"maximum\":" + max + "}"; }
    private static String dateTime() { return "{\"type\":\"string\",\"format\":\"date-time\"}"; }
    private static String enumOf(String... values) { return "{\"type\":\"string\",\"enum\":[\"" + String.join("\",\"", values) + "\"]}"; }
    private static String switches(String csv) { String[] names = csv.split(","); StringBuilder fields = new StringBuilder(); StringBuilder required = new StringBuilder(); for (String name : names) { if (!fields.isEmpty()) { fields.append(','); required.append(','); } fields.append('"').append(name).append("\":{\"type\":\"boolean\"}"); required.append('"').append(name).append('"'); } return object(fields.toString(), required.toString()); }
}
