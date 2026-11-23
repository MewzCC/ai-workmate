package com.aiworkmate.config;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.AiConfiguredModel;
import com.aiworkmate.dto.AiModelCategory;
import com.aiworkmate.mapper.AiConfigurationMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiProviderRuntimeTest {
 @Test void separatesTypedModelsFromChatAndSupportsLegacyRows() {
  var mapper = mock(AiConfigurationMapper.class);
  var cipher = new AiSecretCipher("test-secret");
  when(mapper.load()).thenReturn(Map.of("model", "legacy", "models",
      "[{\"id\":\"legacy\",\"contextWindow\":32768,\"maxOutputTokens\":4096,\"supportsImages\":false}]"));
  var embed = new AiConfiguredModel("embedding", 32768, 4096, false, AiModelCategory.EMBEDDING, true);
  var ocr = new AiConfiguredModel("reader", 32768, 4096, false, AiModelCategory.OCR, false);
  var typed = Map.<String, Object>of("id", 2L, "models", AiModelSettings.write(List.of(embed, ocr)),
      "base_url", "http://127.0.0.1/v1", "encrypted_api_key", cipher.encrypt("typed-test-key"));
  when(mapper.channels()).thenReturn(List.of(typed));
  when(mapper.channel(2L)).thenReturn(typed);
  var runtime = new AiProviderRuntime(mapper, cipher, new AiRuntimeProperties(), "legacy");
  assertThat(runtime.catalog()).extracting(AiConfiguredModel::id).containsExactly("legacy");
  assertThat(runtime.catalog(AiModelCategory.EMBEDDING)).extracting(AiConfiguredModel::id).containsExactly("2::embedding");
  assertThat(runtime.channelModel("2::reader", AiModelCategory.OCR).model()).isEqualTo(ocr);
  assertThatThrownBy(() -> runtime.validateTypedModel("2::reader", AiModelCategory.EMBEDDING))
      .isInstanceOf(BusinessException.class);
  assertThatThrownBy(() -> runtime.supportsImages("2::reader")).isInstanceOf(BusinessException.class);
 }
 @Test void isolatesSameNamedModelsAndRejectsUnknownChannels() {
  var mapper=mock(AiConfigurationMapper.class); var cipher=new AiSecretCipher("test-secret");
  when(mapper.load()).thenReturn(Map.of("model","same"));
  var second=Map.<String,Object>of("id",2L,"model","same","base_url","http://localhost/v1","encrypted_api_key",cipher.encrypt("second-key"),
    "models",AiModelSettings.write(java.util.List.of(new com.aiworkmate.dto.AiConfiguredModel("same",8192,512,true))));
  when(mapper.channels()).thenReturn(java.util.List.of(second)); when(mapper.channel(2L)).thenReturn(second);
  var runtime=new AiProviderRuntime(mapper,cipher,new AiRuntimeProperties(),"legacy");
  assertThat(runtime.catalog()).extracting(com.aiworkmate.dto.AiConfiguredModel::id).containsExactly("same","2::same");
  assertThat(runtime.supportsImages("same")).isFalse(); assertThat(runtime.supportsImages("2::same")).isTrue();
  var prepared=runtime.prepare(new org.springframework.ai.chat.prompt.Prompt("hello",OpenAiChatOptions.builder().model("2::same").build()));
  assertThat(prepared.prompt().getOptions().getModel()).isEqualTo("same");
  assertThatThrownBy(() -> runtime.supportsImages("3::same")).isInstanceOf(com.aiworkmate.common.BusinessException.class);
 }
 @Test void newRequestsReloadConfigurationAndStreamingUsesDefaultModel() throws Exception {
  var bodies=new CopyOnWriteArrayList<String>();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/v1/chat/completions", exchange -> {
   String request=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8); bodies.add(request);
   assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer local-test-key");
   boolean streaming=request.contains("\"stream\":true") || request.contains("\"stream\" : true");
   String body=streaming ? "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"alpha\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"hello\"},\"finish_reason\":null}]}\n\ndata: [DONE]\n\n" : "{\"id\":\"1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"beta\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"hello\"},\"finish_reason\":\"stop\"}]}";
   body=streaming ? body.replace("data: [DONE]", "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"alpha\",\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2,\"total_tokens\":12}}\n\ndata: [DONE]")
     : body.substring(0,body.length()-1)+",\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2,\"total_tokens\":12}}";
   exchange.getResponseHeaders().set("Content-Type",streaming?"text/event-stream":"application/json");
   byte[] data=body.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,data.length); exchange.getResponseBody().write(data); exchange.close();
  }); server.start();
  try {
   var mapper=mock(AiConfigurationMapper.class); var cipher=new AiSecretCipher("test-secret");
   String url="http://127.0.0.1:"+server.getAddress().getPort();
   when(mapper.load()).thenReturn(Map.of("base_url",url,"model","alpha","encrypted_api_key",cipher.encrypt("local-test-key")));
   var usage=mock(com.aiworkmate.service.AiUsageService.class);
   var client=new AiConfig().chatClient(new AiProviderRuntime(mapper,cipher,new AiRuntimeProperties(),"legacy"),usage);
   assertThat(String.join("",client.prompt().user("hello").options(OpenAiChatOptions.builder().model("default").build()).stream().content().collectList().block(Duration.ofSeconds(10)))).isEqualTo("hello");
   when(mapper.load()).thenReturn(Map.of("base_url",url,"model","beta","encrypted_api_key",cipher.encrypt("local-test-key")));
   assertThat(client.prompt().user("hello").call().content()).isEqualTo("hello");
   assertThat(bodies.get(0)).contains("alpha").doesNotContain("default");
   assertThat(bodies.get(1)).contains("beta");
   verify(usage,timeout(1000)).record(eq(1L),eq("alpha"),eq("SUCCESS"),anyLong(),argThat(response -> response.getMetadata().getUsage().getTotalTokens()==12));
   verify(usage).record(eq(1L),eq("beta"),eq("SUCCESS"),anyLong(),argThat(response -> response.getMetadata().getUsage().getTotalTokens()==12));
   verifyNoMoreInteractions(usage);
  } finally { server.stop(0); }
 }
 @Test void enforcesEnabledModelsContextAndImagePolicy() {
  var mapper=mock(AiConfigurationMapper.class); var cipher=new AiSecretCipher("test-secret");
  var policy=new com.aiworkmate.dto.AiConfiguredModel("enabled",1024,256,false);
  when(mapper.load()).thenReturn(Map.of("base_url","http://localhost:1234/v1","model","enabled","models",AiModelSettings.write(java.util.List.of(policy)),"encrypted_api_key",cipher.encrypt("local-test-key")));
  var runtime=new AiProviderRuntime(mapper,cipher,new AiRuntimeProperties(),"legacy");
  assertThat(runtime.supportsImages("default")).isFalse();
  assertThatThrownBy(() -> runtime.prepare(new org.springframework.ai.chat.prompt.Prompt("x".repeat(1024))))
      .isInstanceOf(com.aiworkmate.common.BusinessException.class).extracting("messageKey").isEqualTo("error.aiConfiguration.contextExceeded");
  assertThatThrownBy(() -> runtime.prepare(new org.springframework.ai.chat.prompt.Prompt("hello",OpenAiChatOptions.builder().model("not-enabled").build())))
      .isInstanceOf(com.aiworkmate.common.BusinessException.class);
  var prepared=runtime.prepare(new org.springframework.ai.chat.prompt.Prompt("hello"));
  assertThat(prepared.prompt().getOptions().getMaxTokens()).isEqualTo(256);
 }

}
