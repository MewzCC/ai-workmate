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

    @Test void frozenMultivalueAndReferenceFieldsPreserveOrderAndIdentifiers() {
        var fields = ApprovalApplicationAgentFieldProjection.project(mapper, """
                {"fields":[{"name":"period","type":"dateRange"},{"name":"choices","type":"checkbox"},
                  {"name":"person","type":"user"},{"name":"unit","type":"department"},
                  {"name":"legacy","type":"user"},{"name":"privateUser","type":"user","sensitive":true}]}
                """, """
                {"period":["2026-11-12","2026-11-13"],"choices":["B","A"],"person":9007199254740993,
                 "unit":"9223372036854775807","legacy":"Unverified name","privateUser":"37"}
                """);
        assertThat(fields.get(0).values()).containsExactly("2026-11-12", "2026-11-13");
        assertThat(fields.get(1).values()).containsExactly("B", "A");
        assertThat(fields.get(2).values()).containsExactly("9007199254740993");
        assertThat(fields.get(3).values()).containsExactly("9223372036854775807");
        assertThat(fields.subList(4, 6)).allSatisfy(field -> {
            assertThat(field.redacted()).isTrue();
            assertThat(field.values()).isEmpty();
        });
    }

    @Test void nestedOrOversizedValuesFailInsteadOfPartialSuccess() {
        String schema = "{\"fields\":[{\"name\":\"x\"}]}";
        assertThatThrownBy(() -> ApprovalApplicationAgentFieldProjection.project(mapper, schema, "{\"x\":{\"path\":\"private\"}}"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ApprovalApplicationAgentFieldProjection.project(mapper, schema,
                "{\"x\":\"" + "a".repeat(501) + "\"}" )).isInstanceOf(BusinessException.class);
    }

    @Test void moneyProjectionPreservesExactDecimalDigitsAndRejectsAmbiguousJson() {
        String schema = "{\"fields\":[{\"name\":\"amount\",\"type\":\"money\"},{\"name\":\"bankAccount\",\"type\":\"money\"}]}";
        var fields = ApprovalApplicationAgentFieldProjection.project(mapper, schema, "{\"amount\":9007199254740993.01,\"bankAccount\":1234567}");
        assertThat(fields.get(0).values()).containsExactly("9007199254740993.01");
        assertThat(fields.get(1).redacted()).isTrue(); assertThat(fields.get(1).values()).isEmpty();
        for (String data : new String[]{"{\"amount\":1,\"amount\":2}", "{} {}"})
            assertThatThrownBy(() -> ApprovalApplicationAgentFieldProjection.project(mapper,schema,data)).isInstanceOf(BusinessException.class);
    }
}
