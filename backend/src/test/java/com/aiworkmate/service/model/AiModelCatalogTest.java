package com.aiworkmate.service.model;

import com.aiworkmate.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@org.junit.jupiter.api.extension.ExtendWith(com.aiworkmate.common.LocalizedMessagesExtension.class)
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
    void shouldAllowCustomGatewayModelAndDefault() {
        assertThat(AiModelCatalog.normalize("auto/deepseek-v4.1-flash")).isEqualTo("auto/deepseek-v4.1-flash");
        assertThat(AiModelCatalog.normalize(null)).isEqualTo("default");
    }

    @Test
    void shouldRejectInvalidModelSyntax() {
        assertThatThrownBy(() -> AiModelCatalog.normalize("invalid model"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不支持的 AI 模型");
    }
}
