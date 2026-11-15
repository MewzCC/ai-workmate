package com.aiworkmate.config;

import com.aiworkmate.dto.AiConfiguredModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public final class AiModelSettings {
    private static final ObjectMapper JSON = new ObjectMapper();
    private AiModelSettings() {}
    public static List<AiConfiguredModel> read(Map<String,Object> row, String fallback) {
        if (row == null || row.get("models") == null) return List.of(AiConfiguredModel.defaults(fallback));
        try {
            List<AiConfiguredModel> models = JSON.readValue(row.get("models").toString(), new TypeReference<>() {});
            if (models.isEmpty()) throw new IllegalStateException();
            return List.copyOf(models);
        } catch (Exception e) { throw new IllegalStateException("Invalid AI model configuration"); }
    }
    public static String write(List<AiConfiguredModel> models) {
        try { return JSON.writeValueAsString(models); }
        catch (Exception e) { throw new IllegalStateException("Invalid AI model configuration"); }
    }
    public static String versionedUrl(String input) {
        String url = input.strip().replaceAll("/+$", "");
        return url.endsWith("/v1") ? url : url + "/v1";
    }
}
