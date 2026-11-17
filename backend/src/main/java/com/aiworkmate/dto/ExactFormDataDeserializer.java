package com.aiworkmate.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps form decimals exact without changing the mapper used for frozen tool Schema hashes. */
public final class ExactFormDataDeserializer extends JsonDeserializer<Map<String, Object>> {
    @Override
    public Map<String, Object> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!(parser.getCodec() instanceof ObjectMapper mapper))
            throw JsonMappingException.from(parser, "Unsupported form data codec");
        return mapper.readerFor(new TypeReference<LinkedHashMap<String, Object>>() { })
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readValue(parser);
    }
}
