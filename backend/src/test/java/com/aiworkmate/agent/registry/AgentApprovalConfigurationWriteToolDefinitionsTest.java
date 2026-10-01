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
}
