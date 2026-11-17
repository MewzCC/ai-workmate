package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalApplicationTableArgumentsTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void keepsStructuredRowsAndExactMoneyWithoutParsingModelSuppliedJson() throws Exception {
        var fields = ApprovalApplicationDraftArguments.parseFields(json.readTree("""
                {"fields":[{"name":"items","rows":[{"cells":[
                  {"name":"amount","value":"9007199254740993.01"},
                  {"name":"description","value":"Line one"}]}]}]}
                """));
        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).values()).isEmpty();
        assertThat(fields.get(0).rows().get(0).cells().get(0).value()).isEqualTo("9007199254740993.01");
    }

    @Test
    void rejectsAmbiguityDuplicateCellsNestedValuesAndCommands() throws Exception {
        for (String field : new String[]{
                "{\"name\":\"items\",\"value\":\"x\",\"rows\":[]}",
                "{\"name\":\"items\",\"rows\":[{\"cells\":[{\"name\":\"a\",\"value\":\"1\"},{\"name\":\"a\",\"value\":\"2\"}]}]}",
                "{\"name\":\"items\",\"rows\":[{\"cells\":[{\"name\":\"a\",\"value\":{}}]}]}",
                "{\"name\":\"items\",\"rows\":[{\"cells\":[],\"command\":\"execute\"}]}"}) {
            var arguments = json.readTree("{\"fields\":[" + field + "]}");
            assertThatThrownBy(() -> ApprovalApplicationDraftArguments.parseFields(arguments))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void rejectsOversizedRowsAndCells() {
        var arguments = json.createObjectNode();
        var rows = arguments.putArray("fields").addObject().put("name", "items").putArray("rows");
        for (int row = 0; row < 21; row++) rows.addObject().putArray("cells");
        assertThatThrownBy(() -> ApprovalApplicationDraftArguments.parseFields(arguments))
                .isInstanceOf(BusinessException.class);
        rows.removeAll();
        var cells = rows.addObject().putArray("cells");
        for (int column = 0; column < 11; column++) cells.addObject().put("name", "c" + column).put("value", "x");
        assertThatThrownBy(() -> ApprovalApplicationDraftArguments.parseFields(arguments))
                .isInstanceOf(BusinessException.class);
    }
}
