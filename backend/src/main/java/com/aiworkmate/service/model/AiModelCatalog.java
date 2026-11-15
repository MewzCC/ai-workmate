package com.aiworkmate.service.model;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;

import java.util.Set;

public final class AiModelCatalog {

    public static final String DEFAULT_MODEL = "deepseek-v4-flash";
    public static final String PRO_MODEL = "deepseek-v4-pro";

    private static final String LEGACY_MODEL = "deepseek-chat";

    /** 支持直接接收图片的多模态模型；接入视觉模型时在此登记 */
    private static final Set<String> MULTIMODAL_MODELS = Set.of();

    private AiModelCatalog() {
    }

    public static String normalize(String model) {
        if (model == null || model.isBlank()) {
            return "default";
        }
        if (LEGACY_MODEL.equals(model.strip())) return DEFAULT_MODEL;
        String normalized = model.strip();
        if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.chat.model.invalid");
        }
        return normalized;
    }

    /** 模型是否支持直接接收图片（多模态）；当前默认模型均为纯文本模型 */
    public static boolean isMultimodal(String model) {
        return MULTIMODAL_MODELS.contains(normalize(model));
    }
}
