package com.aiworkmate.service.impl;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.config.*;
import com.aiworkmate.dto.*;
import com.aiworkmate.mapper.AiConfigurationMapper;
import com.aiworkmate.service.*;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiConfigurationServiceImplTest {
 private final AiConfigurationMapper mapper=mock(AiConfigurationMapper.class);
 private final UserAccessService access=mock(UserAccessService.class);
 private final BusinessAuditService audit=mock(BusinessAuditService.class);
 private final AiConfigurationServiceImpl service=new AiConfigurationServiceImpl(mapper,new AiSecretCipher("unit-test-key"),mock(AiProviderRuntime.class),access,audit);
 private void admin() { when(access.resolveActiveUser(1L)).thenReturn(new ResolvedUserAccess(1L,"admin","SUPER_ADMIN",List.of("access:manage"))); }
 @Test void newChannelCannotReusePrimarySecret() {
  admin(); when(mapper.load()).thenReturn(Map.of("encrypted_api_key","primary-secret"));
  assertThatThrownBy(() -> service.saveChannel(1L,2L,new AiChannelRequest("Second",new AiConfigurationRequest("http://host/v1","model","",0L))))
    .isInstanceOf(BusinessException.class);
  verify(mapper,never()).load(); verifyNoInteractions(audit);
 }
 @Test void deniesUnauthenticatedAndNonSuperAdminsBeforeDatabaseOrNetwork() {
  assertThatThrownBy(() -> service.get(1L)).isInstanceOf(BusinessException.class);
  when(access.resolveActiveUser(1L)).thenReturn(new ResolvedUserAccess(1L,"staff","ADMIN",List.of("access:manage")));
  assertThatThrownBy(() -> service.save(1L,new AiConfigurationRequest("http://host","model","key",0L))).isInstanceOf(BusinessException.class);
  assertThatThrownBy(() -> service.test(1L)).isInstanceOf(BusinessException.class);
  verifyNoInteractions(mapper,audit);
 }
 @Test void normalizesBaseUrlAndRejectsCredentialUrls() {
  assertThat(AiConfigurationServiceImpl.normalizeUrl("http://192.168.1.94:18887/v1/")).isEqualTo("http://192.168.1.94:18887/v1");
  for(String url:List.of("file:///etc/passwd","http://user:password@host/v1","http://host/v1?key=secret","http://host/v1/chat/completions"))
   assertThatThrownBy(() -> AiConfigurationServiceImpl.normalizeUrl(url)).isInstanceOf(BusinessException.class);
 }
 @Test void responseDoesNotReturnEncryptedOrPlaintextKey() {
  admin(); when(mapper.load()).thenReturn(Map.of("base_url","http://host","model","model","encrypted_api_key","secret","version",2L));
  assertThat(service.get(1L).toString()).doesNotContain("secret");
 }
 @Test void preservesKeyOnBlankAndRejectsStaleVersion() {
  admin(); when(mapper.load()).thenReturn(Map.of("encrypted_api_key","encrypted-existing"));
  assertThatThrownBy(() -> service.save(1L,new AiConfigurationRequest("http://host/v1","model","",1L))).isInstanceOf(BusinessException.class);
  verify(mapper).update(eq("http://host/v1"),eq("model"),eq("encrypted-existing"),eq(1L),eq(1L),anyString());
  verifyNoInteractions(audit);
 }
 @Test void requiresInitialKey() {
  admin(); when(mapper.load()).thenReturn(null); assertThatThrownBy(() -> service.save(1L,new AiConfigurationRequest("http://host","model","",0L))).isInstanceOf(BusinessException.class);
  verify(mapper,never()).insert(any(),any(),any(),any(),any());
 }
 @Test void discoversVersionedModelEndpointWithoutReturningCredentials() throws Exception {
  admin(); var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/v1/models", exchange -> {
   assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer local-key");
   byte[] bytes="{\"data\":[{\"id\":\"vision/model\",\"context_length\":65536,\"supports_images\":true},{\"id\":\"text/model\"},{\"id\":\"deepseek-flash\",\"context_window\":1048576,\"max_output_tokens\":393216},{\"id\":\"high-output\",\"context_window\":524288,\"max_output_tokens\":393216}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
   exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
  });server.start();
  try {
   var models=service.discover(1L,new AiProviderProbeRequest("http://127.0.0.1:"+server.getAddress().getPort()+"/v1","local-key",null));
   assertThat(models).extracting(AiConfiguredModel::id).containsExactly("deepseek-flash","high-output","text/model","vision/model");
   assertThat(models.get(0).contextWindow()).isEqualTo(1048576);
   assertThat(models.get(0).maxOutputTokens()).isEqualTo(393216);
   assertThat(models.get(1).maxOutputTokens()).isEqualTo(393216);
   assertThat(models.get(2).supportsImages()).isFalse(); assertThat(models.get(3).contextWindow()).isEqualTo(65536);
   assertThat(models.toString()).doesNotContain("local-key");
  } finally {server.stop(0);}
 }
 @Test void rejectsUnselectedDefaultAndInvalidOutputBudget() {
  admin();
  assertThatThrownBy(() -> service.save(1L,new AiConfigurationRequest("http://host/v1","other","key",0L,List.of(AiConfiguredModel.defaults("selected")))))
    .isInstanceOf(BusinessException.class);
  assertThatThrownBy(() -> service.save(1L,new AiConfigurationRequest("http://host/v1","selected","key",0L,List.of(new AiConfiguredModel("selected",1024,2048,false)))))
    .isInstanceOf(BusinessException.class);
  verifyNoInteractions(mapper,audit);
 }

}
