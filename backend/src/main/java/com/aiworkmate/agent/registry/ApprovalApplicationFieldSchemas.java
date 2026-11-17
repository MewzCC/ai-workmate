package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Shares the bounded field input across draft creation, editing and atomic submission. */
final class ApprovalApplicationFieldSchemas {
    private ApprovalApplicationFieldSchemas() { }

    static void addColumnDefinitions(ObjectNode fieldProperties, ObjectMapper json) throws com.fasterxml.jackson.core.JsonProcessingException {
        fieldProperties.set("columns", json.readTree("""
                {"type":"array","minItems":1,"maxItems":10,"items":{"type":"object","additionalProperties":false,
                "required":["name","label","type","required","options"],"properties":{
                "name":{"type":"string","pattern":"^[a-z][A-Za-z0-9_-]{0,39}$","maxLength":40},
                "label":{"type":"string","minLength":1,"maxLength":40},
                "type":{"type":"string","enum":["text","textarea","number","money","date","time","radio","select"]},
                "required":{"type":"boolean"},"options":{"type":"array","maxItems":10,"items":{"type":"string","minLength":1,"maxLength":80}},
                "min":{"type":"number","minimum":-1000000000000000,"maximum":1000000000000000}}}}
                """));
    }

    static String withTemplateColumns(String schema) {
        try {
            var json = new ObjectMapper();
            ObjectNode root = (ObjectNode) json.readTree(schema);
            addColumnDefinitions((ObjectNode) root.at("/properties/items/items/properties/fields/items/properties"), json);
            return json.writeValueAsString(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    static String withTableProjection(String scalarSchema) {
        try {
            var json = new ObjectMapper();
            ObjectNode root = (ObjectNode) json.readTree(scalarSchema);
            ObjectNode fieldProperties = (ObjectNode) root.path("properties").path("fields").path("items").path("properties");
            fieldProperties.set("table", json.readTree("""
                    {"type":"object","additionalProperties":false,"required":["columns","rows"],"properties":{
                    "columns":{"type":"array","minItems":1,"maxItems":10,"items":{"type":"object",
                    "additionalProperties":false,"required":["name","label","type"],"properties":{
                    "name":{"type":"string","maxLength":40},"label":{"type":"string","maxLength":40},
                    "type":{"type":"string","enum":["text","textarea","number","money","date","time","radio","select"]}}}},
                    "rows":{"type":"array","maxItems":20,"items":{"type":"array","maxItems":10,
                    "items":{"type":"string","maxLength":500}}}}}
                    """));
            return json.writeValueAsString(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    static String withTableRows(String scalarSchema) {
        try {
            var json = new ObjectMapper();
            ObjectNode root = (ObjectNode) json.readTree(scalarSchema);
            ObjectNode field = (ObjectNode) root.path("properties").path("fields").path("items");
            ((ObjectNode) field.path("properties")).set("rows", json.readTree("""
                    {"type":"array","maxItems":20,"items":{"type":"object","additionalProperties":false,
                    "required":["cells"],"properties":{"cells":{"type":"array","maxItems":10,"items":{
                    "type":"object","additionalProperties":false,"required":["name","value"],"properties":{
                    "name":{"type":"string","pattern":"^[a-z][A-Za-z0-9_-]{0,39}$","maxLength":40},
                    "value":{"type":"string","maxLength":500}}}}}}}
                    """));
            // With one required alternative per branch, exactly one of value/values/rows is allowed.
            var alternatives = field.putArray("oneOf");
            for (String key : new String[]{"value", "values", "rows"}) {
                alternatives.addObject().putArray("required").add(key);
            }
            return json.writeValueAsString(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
