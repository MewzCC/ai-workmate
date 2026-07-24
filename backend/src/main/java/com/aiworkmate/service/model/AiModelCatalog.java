package com.aiworkmate.service.model;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;

import java.util.Set;

public final class AiModelCatalog {

    public static final String DEFAULT_MODEL = "deepseek-v4-flash";
    public static final String PRO_MODEL = "deepseek-v4-pro";

    private static final String LEGACY_MODEL = "deepseek-chat";
    private static final Set<String> SUPPORTED_MODELS = Set.of(DEFAULT_MODEL, PRO_MODEL);

    private AiModelCatalog() {
    }

    public static String normalize(String model) {
        if (model == null || model.isBlank() || LEGACY_MODEL.equals(model.strip())) {
            return DEFAULT_MODEL;
        }
        String normalized = model.strip();
        if (!SUPPORTED_MODELS.contains(normalized)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "不支持的 AI 模型");
        }
        return normalized;
    }
}
