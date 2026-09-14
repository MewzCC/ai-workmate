package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentReadToolDefinitionsTest {

    @Test
    void visitorAndSealDefinitionsHideSensitiveOperationalFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolSchemaValidator validator = new ToolSchemaValidator();
        ToolDefinition visitor = new AgentReadToolDefinitions().visitorQueryToolDefinition(mapper);
        ToolDefinition seal = new AgentReadToolDefinitions().sealQueryToolDefinition(mapper);
        assertThat(visitor.schemaHash()).isEqualTo(
                "sha256:aa6f923d24734c45f3022026885641deb9b74abbe6e9061394234e5cc739a3aa");
        assertThat(seal.schemaHash()).isEqualTo(
                "sha256:9f896eeb4041562e8c0a7d33ed8117af6c9be6f91d71ce3c763a59d3ce3c0175");
        assertThat(visitor.requiredPermissions()).containsExactly("visitor:read:self");
        assertThat(seal.requiredPermissions()).containsExactly("seal:read:self");
        assertThat(visitor.outputSchema().toString())
                .doesNotContain("visitorPhone", "plateNumber", "UserId", "workflowInstanceId", "taskId");
        assertThat(seal.outputSchema().toString())
                .doesNotContain("storage", "documentId", "UserId", "workflowInstanceId", "taskId");
        assertThat(validator.valid(visitor.inputSchema(), mapper.readTree("{\"bookingId\":7}"))).isTrue();
        assertThat(validator.valid(visitor.inputSchema(), mapper.readTree(
                "{\"bookingId\":7,\"queue\":\"MINE\"}"))).isFalse();
        assertThat(validator.valid(seal.inputSchema(), mapper.readTree(
                "{\"queue\":\"PENDING\",\"size\":50}"))).isTrue();
    }

    @Test
    void attendanceDefinitionUsesOneBoundedDiscriminatedReadContract() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentReadToolDefinitions().attendanceQueryToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:4ff357d3836da753f0c32d3774ea04ba3cf83d1c6b8708de548e8ff711348d60");
        assertThat(definition.requiredPermissions()).containsExactly("attendance:read");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(definition.outputSchema().toString())
                .doesNotContain("tenantId", "userId", "clockInIp", "clockOutIp");
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"resource\":\"EXCEPTIONS\",\"employeeId\":7,\"size\":50}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"resource\":\"STATISTICS\",\"month\":13}"))).isFalse();
    }

    @Test
    void meetingDefinitionIsClosedAndHidesInternalIdentities() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentReadToolDefinitions().meetingQueryToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:48a529200e1b903fe1623c118d9fc78b4504bcc8dbda8202cc056f83420db25b");
        assertThat(definition.requiredPermissions()).containsExactly("meeting:read:self");
        assertThat(definition.outputSchema().toString())
                .doesNotContain("organizerUserId", "cancelledByUserId", "tenantId");
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"roomStatus\":\"OPEN\",\"bookingStatus\":\"BOOKED\",\"size\":50}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"bookingStatus\":\"FINISHED\"}"))).isFalse();
    }

    @Test
    void assetDefinitionIsClosedAndTenantScoped() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentReadToolDefinitions().assetQueryToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();
        assertThat(definition.requiredPermissions()).containsExactly("assets:read");
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:c0c6bae0a712cbbfbef1cc741fb50af140a3cde98603e8bed8a90294ad5810b1");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(definition.outputSchema().toString())
                .doesNotContain("ownerUserId", "departmentId", "tenantId", "history");
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"status\":\"REPAIRING\",\"category\":\"IT\",\"size\":50}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"status\":\"DELETED\"}"))).isFalse();
    }

    @Test
    void employeeChangeDefinitionIsClosedAndTenantScoped() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentReadToolDefinitions().employeeChangeQueryToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();
        assertThat(definition.requiredPermissions()).containsExactly("hr:read");
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:e7776fa61d680c418f2e210d5e0d7dd9d93d3d7d1ab153d7a81c224b8e457c53");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(definition.outputSchema().toString()).doesNotContain("employeeUserId", "applicantUserId");
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"status\":\"PENDING\",\"changeType\":\"TRANSFER\",\"size\":50}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"changeType\":\"DELETE\"}"))).isFalse();
    }

    @Test
    void hrEmployeeDefinitionRequiresOnlyVisibleEmployeeId() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentReadToolDefinitions().hrEmployeeQueryToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();
        assertThat(definition.requiredPermissions()).containsExactly("hr:read");
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:0df65eae1431949822629ffa8927d5f9077dfc87b0aea607495a845f0ea5918c");
        assertThat(definition.outputSchema().toString()).doesNotContain("email", "avatar", "document");
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("{\"employeeId\":7}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("{\"employeeId\":7,\"userId\":8}"))).isFalse();
    }

    @Test
    void hrOrganizationDefinitionIsBoundedAndDropsSensitiveFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolDefinition definition = new AgentReadToolDefinitions().hrOrganizationQueryToolDefinition(objectMapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.code()).isEqualTo("hr.organization.query");
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:abfc7576bc4f76ce773f2eb630548ea7e051e9931ab674660fdcfdaec83fdd9d");
        assertThat(definition.requiredPermissions()).containsExactly("hr:read");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(definition.outputSchema().toString()).doesNotContain("email", "avatar", "permission");
        assertThat(validator.valid(definition.inputSchema(), objectMapper.readTree(
                "{\"keyword\":\"研发\",\"limit\":50}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), objectMapper.readTree(
                "{\"limit\":51}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(), objectMapper.readTree(
                "{\"tenantId\":1}"))).isFalse();
    }

    @Test
    void approvalTaskDefinitionUsesTenantScopeAndClosedBounds() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolDefinition definition = new AgentReadToolDefinitions().approvalTaskQueryToolDefinition(objectMapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.code()).isEqualTo("approval.task.query");
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:5c75860e7c13bb66edf25ddc589a41710a16de8a850c4c7fdb0b1df4d805f876");
        assertThat(definition.requiredPermissions()).containsExactly("approval:read");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(validator.valid(definition.inputSchema(), objectMapper.readTree(
                "{\"status\":\"PENDING\",\"keyword\":\"张三\",\"page\":1,\"size\":50}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), objectMapper.readTree(
                "{\"status\":\"UNKNOWN\"}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(), objectMapper.readTree(
                "{\"tenantId\":9}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(), objectMapper.readTree(
                "{\"size\":51}"))).isFalse();
    }

    @Test
    void approvalConfigurationDefinitionUsesOneClosedDiscriminatedQuery() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolDefinition definition = new AgentReadToolDefinitions()
                .approvalConfigurationQueryToolDefinition(objectMapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.code()).isEqualTo("approval.configuration.query");
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:2e459355b6911204453ea0dc07f403477514a5df1fba877d52c29e48bf08fb22");
        assertThat(definition.requiredPermissions()).containsExactly("approval:read");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"resource\":\"FORM\",\"page\":1,\"size\":50}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"resource\":\"UNKNOWN\"}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"resource\":\"RULE\",\"tenantId\":9}"))).isFalse();
    }

    @Test
    void todoQueryDefinitionMatchesFrozenDatabaseSeed() throws Exception {
        ToolDefinition definition = new AgentReadToolDefinitions()
                .todoQueryToolDefinition(new ObjectMapper());

        assertThat(definition.code()).isEqualTo("todo.query");
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:5587d07883805ecb5810979e984dc33044c68898a9e9aeed4d07c4f3a9793c69");
        assertThat(definition.requiredPermissions()).containsExactly("todo:read");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.ASSIGNED_TO_SELF);
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L0);
        assertThat(definition.maxResultItems()).isEqualTo(50);
        assertThat(definition.inputSchema().path("additionalProperties").asBoolean()).isFalse();
        assertThat(definition.inputSchema().path("properties").has("userId")).isFalse();
        assertThat(definition.inputSchema().path("properties").path("size").path("maximum").asInt())
                .isEqualTo(50);
    }

    @Test
    void todoQuerySchemaRejectsIdentityForgeryUnknownFieldsAndOverLimit() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolDefinition definition = new AgentReadToolDefinitions()
                .todoQueryToolDefinition(objectMapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"status\":\"PENDING\",\"page\":1,\"size\":50}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"userId\":999}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"tenantId\":999}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"size\":51}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"page\":\"1\"}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"filters\":{\"$ref\":\"file:///etc/passwd\"}}"))).isFalse();
    }

    @Test
    void leaveMineDefinitionAndExclusiveListDetailSchemaAreFrozen() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolDefinition definition = new AgentReadToolDefinitions()
                .leaveMineToolDefinition(objectMapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:9b1d1ce3ec13c9c67f969c939c86eb4ab87659bee011ef2d32eded8a40bd26bf");
        assertThat(definition.requiredPermissions()).containsExactly("leave:read:self");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(validator.valid(definition.inputSchema(), objectMapper.readTree("{}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"applicationId\":10}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"applicationId\":10,\"page\":1}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"applicationId\":10,\"userId\":7}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"size\":51}"))).isFalse();
    }

    @Test
    void knowledgeSearchDefinitionCapsRetrievalAndMarksContentUntrusted() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolDefinition definition = new AgentReadToolDefinitions()
                .knowledgeSearchToolDefinition(objectMapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:3f65a4f7a015f1ca051fd5fad0776ed4aeed9228cf5b1059aa63ee84dab9d30f");
        assertThat(definition.requiredPermissions()).containsExactly("knowledge:search");
        assertThat(definition.maxResultItems()).isEqualTo(10);
        assertThat(definition.outputSchema().at("/properties/untrustedContent/const").asBoolean()).isTrue();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"query\":\"policy\",\"topK\":10}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"query\":\"policy\",\"topK\":11}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(),
                objectMapper.readTree("{\"query\":\"policy\",\"url\":\"https://evil.invalid\"}"))).isFalse();
    }

    @Test
    void notificationMineDefinitionRejectsIdentityAndOverLimit() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolDefinition definition = new AgentReadToolDefinitions()
                .notificationMineToolDefinition(objectMapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:14131b81768944254704a23d89ff3ee3498936a000ef2d595c1427b501ed13a1");
        assertThat(definition.requiredPermissions()).containsExactly("notification:read:self");
        assertThat(validator.valid(definition.inputSchema(), objectMapper.readTree("{\"size\":50}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), objectMapper.readTree("{\"size\":51}"))).isFalse();
        assertThat(validator.valid(definition.inputSchema(), objectMapper.readTree("{\"userId\":9}"))).isFalse();
    }
}
