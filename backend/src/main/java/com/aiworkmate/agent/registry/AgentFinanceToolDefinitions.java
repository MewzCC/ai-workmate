package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentFinanceToolDefinitions {
    static final String EXPENSE_IN = listDetail("applicationId", "\"status\":{\"type\":\"string\",\"enum\":[\"DRAFT\",\"PENDING\",\"APPROVED\",\"REJECTED\",\"WITHDRAWN\",\"CANCELLED\"]}");
    static final String BUDGET_IN = listDetail("budgetId", "\"keyword\":" + text(200)
            + ",\"status\":" + text(40) + ",\"fiscalYear\":{\"type\":\"integer\",\"minimum\":2000,\"maximum\":2100}");
    static final String CONTRACT_IN = listDetail("contractId", "\"keyword\":" + text(200)
            + ",\"status\":" + text(40) + ",\"contractType\":" + text(40)
            + ",\"expiryState\":" + text(40));
    static final String SUPPLIER_IN = listDetail("supplierId", "\"keyword\":" + text(200)
            + ",\"status\":" + text(40) + ",\"category\":" + text(80));

    static final String EXPENSE_OUT = page(item("\"id\",\"status\",\"version\"",
            "\"id\":" + id() + ",\"title\":" + text(255) + ",\"amount\":" + number()
                    + ",\"category\":" + text(40) + ",\"expenseDate\":" + date()
                    + ",\"invoiceNumber\":" + text(100) + ",\"reason\":" + text(1000)
                    + ",\"status\":" + text(40) + ",\"version\":" + integer()
                    + ",\"approverName\":" + text(120) + ",\"dueAt\":" + dateTime()
                    + ",\"submittedAt\":" + dateTime() + ",\"overdue\":" + bool()
                    + ",\"canRemind\":" + bool() + ",\"canWithdraw\":" + bool()
                    + ",\"canEditDraft\":" + bool() + ",\"canCancel\":" + bool()));
    static final String BUDGET_OUT = page(item("\"id\",\"code\",\"name\",\"status\",\"version\"",
            "\"id\":" + id() + ",\"code\":" + text(80) + ",\"name\":" + text(200)
                    + ",\"fiscalYear\":" + integer() + ",\"ownerLabel\":" + text(120)
                    + ",\"totalAmount\":" + number() + ",\"occupiedAmount\":" + number()
                    + ",\"spentAmount\":" + number() + ",\"availableAmount\":" + number()
                    + ",\"currency\":" + text(8) + ",\"utilizationPercent\":" + integer()
                    + ",\"alertLevel\":" + text(40) + ",\"status\":" + text(40)
                    + ",\"summary\":" + text(1000) + ",\"version\":" + integer()
                    + ",\"updatedAt\":" + dateTime() + ",\"canManage\":" + bool()
                    + ",\"allowedTransitions\":" + strings()));
    static final String CONTRACT_OUT = page(item("\"id\",\"code\",\"name\",\"status\",\"version\"",
            "\"id\":" + id() + ",\"code\":" + text(80) + ",\"name\":" + text(200)
                    + ",\"contractType\":" + text(40) + ",\"counterpartyName\":" + text(200)
                    + ",\"supplierLabel\":" + text(200) + ",\"ownerLabel\":" + text(120)
                    + ",\"amount\":" + number() + ",\"paidAmount\":" + number()
                    + ",\"currency\":" + text(8) + ",\"signedDate\":" + date()
                    + ",\"startDate\":" + date() + ",\"endDate\":" + date()
                    + ",\"status\":" + text(40) + ",\"fulfillmentStatus\":" + text(40)
                    + ",\"expiryState\":" + text(40) + ",\"daysUntilExpiry\":" + integer()
                    + ",\"summary\":" + text(1000) + ",\"version\":" + integer()
                    + ",\"updatedAt\":" + dateTime() + ",\"canManage\":" + bool()
                    + ",\"canRecordPayment\":" + bool() + ",\"canRemind\":" + bool()));
    static final String SUPPLIER_OUT = page(item("\"id\",\"code\",\"name\",\"status\",\"version\"",
            "\"id\":" + id() + ",\"code\":" + text(80) + ",\"name\":" + text(200)
                    + ",\"shortName\":" + text(120) + ",\"category\":" + text(80)
                    + ",\"supplierLevel\":" + text(40) + ",\"status\":" + text(40)
                    + ",\"paymentTerms\":" + text(500) + ",\"version\":" + integer()
                    + ",\"updatedAt\":" + dateTime() + ",\"canManage\":" + bool()
                    + ",\"allowedTransitions\":" + strings()));

    @Bean ToolDefinition expenseQueryToolDefinition(ObjectMapper m) throws JsonProcessingException { return definition(ToolCode.EXPENSE_QUERY, "Query my expenses", "Returns bounded expense summaries owned by the authenticated user.", "expense:read:self", OwnershipPolicy.SELF, EXPENSE_IN, EXPENSE_OUT, m); }
    @Bean ToolDefinition budgetQueryToolDefinition(ObjectMapper m) throws JsonProcessingException { return definition(ToolCode.BUDGET_QUERY, "Query budgets", "Returns bounded budget summaries after live finance authorization.", "budget:manage", OwnershipPolicy.TENANT_SCOPED, BUDGET_IN, BUDGET_OUT, m); }
    @Bean ToolDefinition contractQueryToolDefinition(ObjectMapper m) throws JsonProcessingException { return definition(ToolCode.CONTRACT_QUERY, "Query contracts", "Returns bounded contract summaries without internal identity fields.", "contract:manage", OwnershipPolicy.TENANT_SCOPED, CONTRACT_IN, CONTRACT_OUT, m); }
    @Bean ToolDefinition supplierQueryToolDefinition(ObjectMapper m) throws JsonProcessingException { return definition(ToolCode.SUPPLIER_QUERY, "Query suppliers", "Returns bounded supplier summaries without contact or credit identifiers.", "supplier:manage", OwnershipPolicy.TENANT_SCOPED, SUPPLIER_IN, SUPPLIER_OUT, m); }

    private ToolDefinition definition(ToolCode code, String name, String description, String permission,
                                      OwnershipPolicy scope, String input, String output, ObjectMapper m) throws JsonProcessingException {
        return ToolDefinitionFactory.read(code, name, description, "Display an authorized finance summary.",
                m.readTree(input), m.readTree(output), Set.of(permission), scope, 50, 196608, 15000);
    }
    private static String listDetail(String id, String filters) {
        return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"" + id + "\":" + id() + "," + filters + ",\"page\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":10000},\"size\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":50}},\"oneOf\":[{\"required\":[\"" + id + "\"]},{\"not\":{\"required\":[\"" + id + "\"]}}]}";
    }
    private static String page(String item) { return "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"items\",\"total\",\"page\",\"size\"],\"properties\":{\"items\":{\"type\":\"array\",\"maxItems\":50,\"items\":" + item + "},\"total\":{\"type\":\"integer\",\"minimum\":0},\"page\":{\"type\":\"integer\",\"minimum\":1},\"size\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":50}}}"; }
    private static String item(String required, String properties) { return "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[" + required + "],\"properties\":{" + properties + "}}"; }
    private static String text(int max) { return "{\"type\":\"string\",\"maxLength\":" + max + "}"; }
    private static String id() { return "{\"type\":\"integer\",\"minimum\":1}"; }
    private static String integer() { return "{\"type\":\"integer\"}"; }
    private static String number() { return "{\"type\":\"number\"}"; }
    private static String bool() { return "{\"type\":\"boolean\"}"; }
    private static String date() { return "{\"type\":\"string\",\"format\":\"date\"}"; }
    private static String dateTime() { return "{\"type\":\"string\",\"maxLength\":32}"; }
    private static String strings() { return "{\"type\":\"array\",\"maxItems\":20,\"items\":{\"type\":\"string\",\"maxLength\":40}}"; }
}
