package com.aiworkmate.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.stereotype.Component;

@Component
public class ToolSchemaValidator {
    private final SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    public boolean valid(JsonNode schema, JsonNode value) {
        return schema != null && value != null && registry.getSchema(schema).validate(value).isEmpty();
    }
}
