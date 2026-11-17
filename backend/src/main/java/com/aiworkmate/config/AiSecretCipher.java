package com.aiworkmate.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
@Component
public class AiSecretCipher {
 private final SecretKeySpec key;
 public AiSecretCipher(@Value("${jwt.secret}") String secret) {
  try { key=new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(("ai-provider-v1:"+secret).getBytes(StandardCharsets.UTF_8)),"AES"); }
  catch(Exception e){ throw new IllegalStateException("AI encryption unavailable"); }
 }
 public String encrypt(String value) {
  try {
   byte[] iv=new byte[12]; new SecureRandom().nextBytes(iv);
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));
   return Base64.getEncoder().encodeToString(iv)+":"+Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
  } catch(Exception e){ throw new IllegalStateException("AI encryption unavailable"); }
 }
 public String decrypt(String value) {
  try {
   String[] parts=value.split(":",2); Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));
   return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.UTF_8);
  } catch(Exception e){ throw new IllegalStateException("AI credential cannot be decrypted"); }
 }
}
