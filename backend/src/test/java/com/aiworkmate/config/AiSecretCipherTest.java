package com.aiworkmate.config;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AiSecretCipherTest {
 @Test void roundTripUsesRandomIvAndDetectsTampering() {
  var cipher=new AiSecretCipher("test-secret-never-use-in-production");
  String encrypted=cipher.encrypt("test-key");
  assertThat(encrypted).doesNotContain("test-key").isNotEqualTo(cipher.encrypt("test-key"));
  assertThat(cipher.decrypt(encrypted)).isEqualTo("test-key");
  assertThatThrownBy(() -> new AiSecretCipher("different-secret").decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
 }
}
