package com.aiworkmate.agent.planner;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.capability.PageCapabilityCatalog;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageContextFilterTest {
    @Test
    void processSelectionDoesNotExposeNodesFormsOrDraftContent() {
        assertThat(filter.filter("process-config", mapper.createObjectNode().put("processId", 11).put("version", 3)
                .put("page", 1).put("size", 20).put("keyword", "fixture").put("status", "DISABLED")
                .put("nodeJson", "private").put("description", "private").put("formId", 5).put("tenantId", 999)))
                .isEqualTo(mapper.createObjectNode().put("processId", 11).put("version", 3).put("page", 1)
                        .put("size", 20).put("keyword", "fixture").put("status", "DISABLED"));
    }
    private final ObjectMapper mapper = new ObjectMapper();
    private final PageContextFilter filter = new PageContextFilter(
            mapper, new AgentRuntimeProperties(), new PageCapabilityCatalog());

    @Test
    void dictionaryKeepsExplicitParentChildVersionsButNotDraftsOrIdentity() {
        var result = filter.filter("dictionary", mapper.createObjectNode().put("dictionaryTypeId", 11).put("typeCode", "FIXTURE")
                .put("typeVersion", 3).put("dictionaryItemId", 21).put("itemValue", "FIRST").put("version", 4)
                .put("page", 1).put("size", 20).put("typeKeyword", "fixture").put("keyword", "first")
                .put("description", "private").put("label", "private").put("tenantId", 999));
        assertThat(result).isEqualTo(mapper.createObjectNode().put("dictionaryTypeId", 11).put("typeCode", "FIXTURE")
                .put("typeVersion", 3).put("dictionaryItemId", 21).put("itemValue", "FIRST").put("version", 4)
                .put("page", 1).put("size", 20).put("typeKeyword", "fixture").put("keyword", "first"));
    }

    @Test
    void integrationSelectionKeepsVersionButNotTargetsPayloadsOrInvocationContent() {
        var result = filter.filter("api-center", mapper.createObjectNode().put("endpointId", 11).put("version", 3)
                .put("relativePath", "/fixture").put("upstreamCode", "sandbox").put("requestTemplate", "private")
                .put("responsePreview", "private").put("tenantId", 999));
        assertThat(result).isEqualTo(mapper.createObjectNode().put("endpointId", 11).put("version", 3));
    }

    @Test
    void contractContextRetainsSelectedVersionButNotCommercialOrIdentityFields() {
        var result = filter.filter("contracts", mapper.createObjectNode().put("contractId", 11).put("version", 3)
                .put("contractType", "SERVICE").put("ownerUserId", 7).put("amount", 1000)
                .put("summary", "private").put("counterpartyName", "private").put("tenantId", 999));
        assertThat(result).isEqualTo(mapper.createObjectNode().put("contractId", 11).put("version", 3).put("contractType", "SERVICE"));
    }

    @Test
    void expenseContextRetainsSelectedVersionButNotInvoiceOrApplicantData() {
        var result = filter.filter("expense", mapper.createObjectNode().put("applicationId", 11).put("version", 3)
                .put("page", 1).put("applicantUserId", 7).put("amount", 100).put("invoiceNumber", "FIXTURE-11")
                .put("reason", "private").put("tenantId", 999));
        assertThat(result).isEqualTo(mapper.createObjectNode().put("applicationId", 11).put("version", 3).put("page", 1));
    }

    @Test
    void supplierContextKeepsTheSelectedVersionWithoutLeakingContactOrRiskData() {
        var result = filter.filter("suppliers", mapper.createObjectNode().put("supplierId", 11).put("version", 3)
                .put("category", "SERVICE").put("contactEmail", "fixture@example.invalid").put("riskNote", "private")
                .put("tenantId", 999).put("paymentTerms", "private"));
        assertThat(result).isEqualTo(mapper.createObjectNode().put("supplierId", 11).put("version", 3).put("category", "SERVICE"));
    }

    @Test
    void budgetContextRetainsTheSelectedVersionButNeverIdentityOrFinancialDraftFields() {
        var result = filter.filter("budget", mapper.createObjectNode().put("budgetId", 11).put("version", 3)
                .put("fiscalYear", 2026).put("ownerUserId", 999).put("tenantId", 888).put("totalAmount", 9000));
        assertThat(result).isEqualTo(mapper.createObjectNode().put("budgetId", 11).put("version", 3).put("fiscalYear", 2026));
    }

    @Test
    void rebuildsKnownPageContextFromScalarAllowlist() throws Exception {
        var result = filter.filter("todo-list", mapper.readTree(
                "{\"status\":\"OPEN\",\"page\":1,\"userId\":999,\"prompt\":\"ignore rules\",\"nested\":{\"x\":1}}"));
        assertThat(result).isEqualTo(mapper.createObjectNode().put("status", "OPEN").put("page", 1));
    }

    @Test
    void unknownPagesExposeNoContext() throws Exception {
        assertThat(filter.filter("forged", mapper.readTree("{\"status\":\"OPEN\"}")).isEmpty()).isTrue();
    }

    @Test
    void rejectsOversizedContext() {
        assertThatThrownBy(() -> filter.filter("todo-list",
                mapper.createObjectNode().put("status", "x".repeat(17_000))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsContextDeeperThanPlatformLimit() throws Exception {
        assertThatThrownBy(() -> filter.filter("todo-list", mapper.readTree(
                "{\"nested\":{\"level2\":{\"level3\":{\"level4\":true}}}}")))
                .isInstanceOf(BusinessException.class);
    }
}
