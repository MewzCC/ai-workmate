package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ApprovalApplicationAgentFieldProjectionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void projectsOnlySnapshotFieldsAndHidesSensitiveAndAttachments() {
        var fields = ApprovalApplicationAgentFieldProjection.project(mapper, """
                {"fields":[{"name":"reason","label":"Historic reason","type":"textarea"},
                 {"name":"api_key","type":"text"},{"name":"attachment","type":"upload"},
                 {"name":"privateNote","type":"text","sensitive":true},{"name":"choices","type":"select"}]}
                """, """
                {"reason":"A real reason","api_key":"SECRET","attachment":{"path":"/private"},
                 "privateNote":"PRIVATE","newField":"NOT IN SNAPSHOT","choices":["A","B"]}
                """);
        assertThat(fields).hasSize(5);
        assertThat(fields.get(0).label()).isEqualTo("Historic reason");
        assertThat(fields.get(0).values()).containsExactly("A real reason");
        assertThat(fields.get(4).values()).containsExactly("A", "B");
        assertThat(fields.subList(1, 4)).allSatisfy(field -> {
            assertThat(field.redacted()).isTrue();
            assertThat(field.values()).isEmpty();
        });
        assertThat(fields.toString()).doesNotContain("SECRET", "PRIVATE", "/private", "NOT IN SNAPSHOT");
    }

    @Test void malformedOrDuplicateSchemaDoesNotFallbackToRawData() {
        for (String schema : new String[]{"{}", "not-json", "{\"fields\":[{\"name\":\"x\"},{\"name\":\"x\"}]}"}) {
            assertThatThrownBy(() -> ApprovalApplicationAgentFieldProjection.project(mapper, schema, "{\"x\":\"private\"}"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test void nestedOrOversizedValuesFailInsteadOfPartialSuccess() {
        String schema = "{\"fields\":[{\"name\":\"x\"}]}";
        assertThatThrownBy(() -> ApprovalApplicationAgentFieldProjection.project(mapper, schema, "{\"x\":{\"path\":\"private\"}}"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ApprovalApplicationAgentFieldProjection.project(mapper, schema,
                "{\"x\":\"" + "a".repeat(501) + "\"}" )).isInstanceOf(BusinessException.class);
    }
}
