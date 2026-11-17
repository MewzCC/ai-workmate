package com.aiworkmate.config;

import com.aiworkmate.dto.AiConfiguredModel;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AiConfiguredModelValidationTest {
 @Test void acceptsDeepSeek384KOutputAndRejectsAbovePlatformLimit() {
  try(var factory=Validation.buildDefaultValidatorFactory()) {
   var validator=factory.getValidator();
   assertThat(validator.validate(new AiConfiguredModel("deepseek-flash",1048576,393216,false))).isEmpty();
   assertThat(validator.validate(new AiConfiguredModel("deepseek-flash",1048576,393217,false))).isNotEmpty();
  }
 }
}
