package com.aiworkmate.dto;
import java.util.List;
public record AiConfigurationResponse(String baseUrl, String model, boolean keyConfigured, long version,
                                      String source, List<AiConfiguredModel> models) {}
