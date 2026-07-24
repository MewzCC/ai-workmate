package com.aiworkmate.service.model;

import com.aiworkmate.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiModelCatalogTest {

    @Test
    void shouldMigrateLegacyModelToV4Flash() {
        assertThat(AiModelCatalog.normalize("deepseek-chat"))
                .isEqualTo(AiModelCatalog.DEFAULT_MODEL);
    }

    @Test
    void shouldAllowV4Models() {
        assertThat(AiModelCatalog.normalize("deepseek-v4-flash"))
                .isEqualTo(AiModelCatalog.DEFAULT_MODEL);
        assertThat(AiModelCatalog.normalize("deepseek-v4-pro"))
                .isEqualTo(AiModelCatalog.PRO_MODEL);
    }

    @Test
    void shouldRejectUnknownModel() {
        assertThatThrownBy(() -> AiModelCatalog.normalize("unknown-model"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不支持的 AI 模型");
    }
}
