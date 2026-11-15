package com.aiworkmate.config;
import com.aiworkmate.mapper.AiConfigurationMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiProviderRuntimeTest {
 @Test void newRequestsReloadConfigurationAndStreamingUsesDefaultModel() throws Exception {
  var bodies=new CopyOnWriteArrayList<String>();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/v1/chat/completions", exchange -> {
   String request=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8); bodies.add(request);
   assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer local-test-key");
   boolean streaming=request.contains("\"stream\":true") || request.contains("\"stream\" : true");
   String body=streaming ? "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"alpha\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"hello\"},\"finish_reason\":null}]}\n\ndata: [DONE]\n\n" : "{\"id\":\"1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"beta\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"hello\"},\"finish_reason\":\"stop\"}]}";
   exchange.getResponseHeaders().set("Content-Type",streaming?"text/event-stream":"application/json");
   byte[] data=body.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,data.length); exchange.getResponseBody().write(data); exchange.close();
  }); server.start();
  try {
   var mapper=mock(AiConfigurationMapper.class); var cipher=new AiSecretCipher("test-secret");
   String url="http://127.0.0.1:"+server.getAddress().getPort();
   when(mapper.load()).thenReturn(Map.of("base_url",url,"model","alpha","encrypted_api_key",cipher.encrypt("local-test-key")));
   var client=new AiConfig().chatClient(new AiProviderRuntime(mapper,cipher,new AiRuntimeProperties(),"legacy"));
   assertThat(String.join("",client.prompt().user("hello").options(OpenAiChatOptions.builder().model("default").build()).stream().content().collectList().block(Duration.ofSeconds(10)))).isEqualTo("hello");
   when(mapper.load()).thenReturn(Map.of("base_url",url,"model","beta","encrypted_api_key",cipher.encrypt("local-test-key")));
   assertThat(client.prompt().user("hello").call().content()).isEqualTo("hello");
   assertThat(bodies.get(0)).contains("alpha").doesNotContain("default");
   assertThat(bodies.get(1)).contains("beta");
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
