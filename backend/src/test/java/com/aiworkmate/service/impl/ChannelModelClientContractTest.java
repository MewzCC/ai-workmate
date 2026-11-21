package com.aiworkmate.service.impl;

import com.aiworkmate.config.AiProviderRuntime;
import com.aiworkmate.config.AiProviderRuntime.ChannelModel;
import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.config.OcrProperties;
import com.aiworkmate.config.RerankProperties;
import com.aiworkmate.config.UploadProperties;
import com.aiworkmate.dto.AiConfiguredModel;
import com.aiworkmate.dto.AiModelCategory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelModelClientContractTest {
    private final ObjectMapper json = new ObjectMapper();

    @TempDir
    Path temp;

    @Test
    void usesTheSelectedChannelCredentialsAndProtocolForEachCategory() throws Exception {
        var requests = new ConcurrentHashMap<String, JsonNode>();
        var tokens = new ConcurrentHashMap<String, String>();
        var gateway = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gateway.createContext("/v1/embeddings", exchange -> reply(exchange, requests, tokens, Map.of("data",
                List.of(Map.of("index", 0, "embedding", Collections.nCopies(1024, 0.25))))));
        gateway.createContext("/v1/rerank", exchange -> reply(exchange, requests, tokens, Map.of("results",
                List.of(Map.of("index", 1, "relevance_score", 0.9)))));
        gateway.createContext("/v1/chat/completions", exchange -> reply(exchange, requests, tokens, Map.of("choices",
                List.of(Map.of("finish_reason", "stop", "message", Map.of("content", "Invoice 42"))))));
        gateway.start();
        try {
            var runtime = mock(AiProviderRuntime.class);
            String base = "http://127.0.0.1:" + gateway.getAddress().getPort() + "/v1";
            when(runtime.channelModel("2::embed", AiModelCategory.EMBEDDING))
                    .thenReturn(model(base, "embed", AiModelCategory.EMBEDDING, true));
            when(runtime.channelModel("2::rank", AiModelCategory.RERANK))
                    .thenReturn(model(base, "rank", AiModelCategory.RERANK, false));
            when(runtime.channelModel("2::read", AiModelCategory.OCR))
                    .thenReturn(model(base, "read", AiModelCategory.OCR, false));

            var embeddingProperties = new EmbeddingProperties();
            var knowledge = new ChannelKnowledgeModels(runtime, new ApiEmbeddingProviderClient(embeddingProperties),
                    new SiliconflowRerankService(new RerankProperties(), embeddingProperties));
            var vectors = knowledge.embed("2::embed", List.of("sample"));
            assertThat(vectors.model()).isEqualTo("2::embed");
            assertThat(vectors.vectors().get(0)).hasSize(1024);
            assertThat(knowledge.rerank("2::rank", "query", List.of("one", "two"), 2).get(0).index()).isEqualTo(1);

            Path image = Files.write(temp.resolve("sample.png"), new byte[]{1, 2, 3});
            var ocrProperties = new OcrProperties();
            ocrProperties.setEnabled(false);
            var ocr = new ChannelOcrRecognition(runtime, ocrProperties, new UploadProperties());
            assertThat(ocr.recognize("2::read", image, "image/png")).isEqualTo("Invoice 42");

            assertThat(requests.get("/v1/embeddings").path("dimensions").asInt()).isEqualTo(1024);
            assertThat(requests.get("/v1/embeddings").path("model").asText()).isEqualTo("embed");
            assertThat(requests.get("/v1/rerank").path("model").asText()).isEqualTo("rank");
            assertThat(requests.get("/v1/chat/completions").path("model").asText()).isEqualTo("read");
            assertThat(requests.get("/v1/chat/completions").toString()).contains("data:image/png;base64,AQID");
            assertThat(tokens.values()).containsOnly("Bearer local-test-token").hasSize(3);
        } finally {
            gateway.stop(0);
        }
    }

    private ChannelModel model(String base, String id, AiModelCategory category, boolean dimensions) {
        return new ChannelModel(base, "local-test-token", new AiConfiguredModel(id, 32768, 4096, false, category, dimensions));
    }

    private void reply(HttpExchange exchange, Map<String, JsonNode> requests, Map<String, String> tokens,
                       Object response) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            requests.put(path, json.readTree(exchange.getRequestBody()));
            tokens.put(path, exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = json.writeValueAsString(response).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
