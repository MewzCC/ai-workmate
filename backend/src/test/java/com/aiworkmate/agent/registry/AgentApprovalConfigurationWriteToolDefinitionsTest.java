package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentApprovalConfigurationWriteToolDefinitionsTest {
    @Test
    void createFormIsOneConfirmedUnpublishedTenantWrite() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentApprovalConfigurationWriteToolDefinitions()
                .approvalFormCreateDraftToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:8d37f2c2e3a5181b9dfdf2891550c7f44e6e10fc2264c07924c58a603c81402c");
        assertThat(definition.requiredPermissions()).containsExactly("approval:manage");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"formKey":"travel","formName":"出差申请","fields":[
                  {"name":"reason","label":"出差事由","type":"textarea","required":true,"width":"full"}
                ]}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"formKey":"travel","formName":"出差申请","status":"ENABLED","fields":[
                  {"name":"reason","label":"出差事由","type":"textarea","required":true,"width":"full"}
                ]}
                """))).isFalse();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"formKey":"travel","formName":"出差申请","schemaJson":"{}","fields":[]}
                """))).isFalse();
    }

    @Test
    void updateFormIsOneVersionBoundUnpublishedResourceWrite() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentApprovalConfigurationWriteToolDefinitions()
                .approvalFormUpdateDraftToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:e7466382448ff0ccc7ed0fea44a49b95ac4016b70da63497a017d70e8712546a");
        assertThat(definition.requiredPermissions()).containsExactly("approval:manage");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"formId":31,"version":2,"formName":"出差申请","fields":[
                  {"name":"reason","label":"出差事由","type":"textarea","required":true,"width":"full"}
                ]}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"formId":31,"version":2,"formName":"出差申请","status":"ENABLED","fields":[]}
                """))).isFalse();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"formId":31,"version":2,"formKey":"changed","formName":"出差申请","fields":[]}
                """))).isFalse();
    }

    @Test
    void publishFormRequiresSecondaryConfirmationAndCannotEditContent() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentApprovalConfigurationWriteToolDefinitions()
                .approvalFormPublishDraftToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:f7edbba87f8613de2b780a4d15083b76b995be9031eb23da1432771f854085e9");
        assertThat(definition.requiredPermissions()).containsExactly("approval:manage");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"formId\":31,\"version\":2}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"formId\":31,\"version\":2,\"status\":\"ENABLED\",\"schemaJson\":\"{}\"}"))).isFalse();
    }

    @Test
    void createProcessAcceptsSemanticNodesButNotRawJsonOrPublishStatus() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentApprovalConfigurationWriteToolDefinitions()
                .approvalProcessCreateDraftToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:6875532a42b510541241e91f7f4b71526d5f613d16a40f724728fc991c3ea23d");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"processKey":"travel","processName":"出差审批","nodes":[
                  {"nodeType":"START","nodeName":"开始"},
                  {"nodeType":"APPROVAL","nodeName":"主管审批","approveType":"DIRECT_MANAGER","mode":"OR_SIGN"},
                  {"nodeType":"END","nodeName":"结束"}]}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"processKey":"travel","processName":"出差审批","status":"ENABLED","nodeJson":"[]","nodes":[]}
                """))).isFalse();
    }

    @Test
    void updateProcessIsOneVersionBoundUnpublishedResourceWrite() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentApprovalConfigurationWriteToolDefinitions()
                .approvalProcessUpdateDraftToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:034f079551643c41dfbf3bf2f446205c7355890f63edbdff49a5b5daf6aa07f8");
        assertThat(definition.requiredPermissions()).containsExactly("approval:manage");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"processId":41,"version":2,"processName":"出差审批（新版）","nodes":[
                  {"nodeType":"START","nodeName":"开始"},
                  {"nodeType":"APPROVAL","nodeName":"主管审批","approveType":"DIRECT_MANAGER","mode":"OR_SIGN"},
                  {"nodeType":"END","nodeName":"结束"}]}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"processId":41,"version":2,"processKey":"changed","processName":"出差审批","nodes":[]}
                """))).isFalse();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"processId":41,"version":2,"processName":"出差审批","status":"ENABLED","nodeJson":"[]","nodes":[]}
                """))).isFalse();
    }

    @Test
    void publishProcessRequiresSecondaryConfirmationAndCannotEditNodes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentApprovalConfigurationWriteToolDefinitions()
                .approvalProcessPublishDraftToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:3be228efd5386ea292967cb9bf6e027e051798b2ee67b2c8dbff6b0af5fd1113");
        assertThat(definition.requiredPermissions()).containsExactly("approval:manage");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"processId\":41,\"version\":2}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"processId\":41,\"version\":2,\"status\":\"ENABLED\",\"nodeJson\":\"[]\"}"))).isFalse();
    }
    @Test
    void createRuleAcceptsSemanticConditionsButNotRawJsonOrEnableStatus() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentApprovalConfigurationWriteToolDefinitions()
                .approvalRuleCreateDraftToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:847518614624e4e86c659cf1656265ae7e76786a006538d58f2277276c5acc11");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"ruleKey":"large-expense","ruleName":"大额费用复核","ruleType":"AMOUNT_THRESHOLD",
                 "priority":10,"logic":"AND","conditions":[{"field":"amount","operator":"gte","value":"5000"}],
                 "action":{"appendNode":"FINANCE_REVIEW","enabled":true,"mode":"OR_SIGN"}}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"ruleKey":"large-expense","ruleName":"大额费用复核","ruleType":"AMOUNT_THRESHOLD",
                 "priority":10,"logic":"AND","conditions":[],"conditionJson":"{}","actionJson":"{}","status":"ENABLED",
                 "action":{"appendNode":"FINANCE_REVIEW","enabled":true,"mode":"OR_SIGN"}}
                """))).isFalse();
    }

    @Test
    void updateRuleIsOneVersionBoundDisabledResourceWrite() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentApprovalConfigurationWriteToolDefinitions()
                .approvalRuleUpdateDraftToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:ae1d105826a8bb98979b5db5b997b25b35791c1d4b1c5c9e4c20976c83eab733");
        assertThat(definition.requiredPermissions()).containsExactly("approval:manage");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"ruleId":51,"version":2,"ruleName":"大额费用复核（新版）","ruleType":"AMOUNT_THRESHOLD",
                 "priority":5,"logic":"AND","conditions":[{"field":"amount","operator":"gte","value":"8000"}],
                 "action":{"appendNode":"FINANCE_REVIEW","enabled":true,"mode":"OR_SIGN"}}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"ruleId":51,"version":2,"ruleKey":"changed","ruleName":"规则","ruleType":"AMOUNT_THRESHOLD",
                 "priority":5,"logic":"AND","conditions":[],"status":"ENABLED","conditionJson":"{}",
                 "action":{"appendNode":"FINANCE_REVIEW","enabled":true,"mode":"OR_SIGN"}}
                """))).isFalse();
    }

    @Test
    void enableRuleRequiresSecondaryConfirmationAndCannotEditConditions() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentApprovalConfigurationWriteToolDefinitions()
                .approvalRuleEnableDraftToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:a3e22a58f7f77633f402e444b7bf4f3555bf7fde854a4c8819b47233ee762740");
        assertThat(definition.requiredPermissions()).containsExactly("approval:manage");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"ruleId\":51,\"version\":2}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"ruleId\":51,\"version\":2,\"status\":\"ENABLED\",\"conditionJson\":\"{}\"}"))).isFalse();
    }
}
