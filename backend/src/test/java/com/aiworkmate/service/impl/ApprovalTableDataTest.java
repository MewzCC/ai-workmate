package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ApprovalTableDataTest {
    private final ObjectMapper json = new ObjectMapper();
    private static final String SCHEMA = """
            {"type":"table","columns":[{"name":"item","label":"Item","type":"text","required":true},
                {"name":"cost","label":"Cost","type":"money","min":0.01},
                {"name":"kind","label":"Kind","type":"select","options":["A","B"]}]}
            """;

    @Test void keepsMoneyExactAndPermitsIncompleteDraftRows() throws Exception {
        var columns = ApprovalTableData.columns(json.readTree(SCHEMA));
        var row = Map.of("item", "Proof", "cost", "9007199254740993.123456789", "kind", "A");
        ApprovalTableData.validate(List.of(row), columns, true);
        assertThat(row.get("cost")).isEqualTo("9007199254740993.123456789");
        ApprovalTableData.validate(List.of(Map.of()), columns, false);
        assertThatThrownBy(() -> ApprovalTableData.validate(List.of(Map.of()), columns, true)).isInstanceOf(BusinessException.class);
    }

    @Test void rejectsReservedRootFieldNamesWithoutBanningScalarColumnKeys() throws Exception {
        for (String name : List.of("constructor", "prototype", "__proto__")) {
            var definition = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(SCHEMA);
            definition.put("name", name);
            assertThatThrownBy(() -> ApprovalTableData.columns(definition)).isInstanceOf(BusinessException.class);
        }
        var columns = ApprovalTableData.columns(json.readTree(SCHEMA.replace("\"name\":\"item\"", "\"name\":\"constructor\"")));
        ApprovalTableData.validate(List.of(Map.of("constructor", "A safe cell")), columns, false);
    }

    @Test void normalizesNumericExponentLosslesslyAndRejectsIncompleteChoicesAndOversizedExpansion() throws Exception {
        var columns = ApprovalTableData.columns(json.readTree(SCHEMA));
        var original = List.of(Map.of("cost", new java.math.BigDecimal("1E+3")));
        assertThat(ApprovalTableData.normalize(original, columns, false).get(0)).containsEntry("cost", "1000");
        assertThat(original.get(0).get("cost")).isInstanceOf(java.math.BigDecimal.class);
        assertThatThrownBy(() -> ApprovalTableData.validate(List.of(Map.of("cost", new java.math.BigDecimal("1E-2147483647"))), columns, false))
                .isInstanceOf(BusinessException.class);
        for (String type : List.of("select", "radio")) {
            var definition = json.readTree("{\"columns\":[{\"name\":\"choice\",\"label\":\"Choice\",\"type\":\"" + type + "\",\"options\":[]}]}");
            assertThatThrownBy(() -> ApprovalTableData.columns(definition)).isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> ApprovalTableData.validate(List.of(Map.of("item", true)), columns, false)).isInstanceOf(BusinessException.class);
    }

    @Test void rejectsUnknownColumnsDuplicateSchemaNestedDataAndCapacityOverflow() throws Exception {
        var columns = ApprovalTableData.columns(json.readTree(SCHEMA));
        for (Object rows : List.of(List.of(Map.of("unknown", "A")), List.of(Map.of("item", Map.of("script", "bad"))),
                Collections.nCopies(21, Map.of("item", "A")), List.of(Map.of("cost", "0.001")), List.of(Map.of("kind", "UNKNOWN"))))
            assertThatThrownBy(() -> ApprovalTableData.validate(rows, columns, false)).isInstanceOf(BusinessException.class);
        for (String extra : List.of("\"script\":\"run\"", "\"columns\":[]", "\"expression\":\"value\"")) {
            var definition = json.readTree("{\"columns\":[{\"name\":\"item\",\"label\":\"Item\",\"type\":\"text\"," + extra + "}]}");
            assertThatThrownBy(() -> ApprovalTableData.columns(definition)).isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> ApprovalTableData.columns(json.readTree(SCHEMA.replace("\"name\":\"cost\"", "\"name\":\"item\"")))).isInstanceOf(BusinessException.class);
    }
}
