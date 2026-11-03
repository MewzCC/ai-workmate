package com.aiworkmate.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ExactFormDataDeserializerTest {
    @ParameterizedTest @ValueSource(strings = { "draft", "update", "submit" })
    void preservesDecimalsAtTheFormBoundaryWithoutChangingFrozenSchemaSerialization(String kind) throws Exception {
        var json = new ObjectMapper();
        String schemaBefore = json.writeValueAsString(json.readTree("{\"maximum\":1e15}"));
        String values = "\"formData\":{\"amount\":1000000000000000.01,\"reason\":\"Synthetic reason\",\"enabled\":true}";
        Map<String, Object> fields = switch (kind) {
            case "draft" -> json.readValue("{\"formKey\":\"test\"," + values + ",\"processKey\":\"review\"}", ApprovalDraftRequest.class).formData();
            case "update" -> json.readValue("{" + values + ",\"version\":3,\"processKey\":\"review\"}", ApprovalDraftUpdateRequest.class).formData();
            default -> json.readValue("{" + values + ",\"formKey\":\"test\",\"processKey\":\"review\"}", ApprovalSubmitRequest.class).formData();
        };
        assertThat(fields.get("amount")).isEqualTo(new BigDecimal("1000000000000000.01"));
        assertThat(fields).containsEntry("reason", "Synthetic reason").containsEntry("enabled", true);
        assertThat(json.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)).isFalse();
        assertThat(json.writeValueAsString(json.readTree("{\"maximum\":1e15}"))).isEqualTo(schemaBefore);
    }
}
