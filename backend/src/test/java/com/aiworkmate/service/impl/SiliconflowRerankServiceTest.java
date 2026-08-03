package com.aiworkmate.service.impl;

import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.config.RerankProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SiliconflowRerankServiceTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldCallSiliconflowRerankContractAndReturnRankedOrder() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server = server("/v1/rerank", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, """
                    {"id":"rerank-1","model":"Qwen/Qwen3-Reranker-4B",
                     "results":[{"index":1,"relevance_score":0.95},{"index":0,"relevance_score":0.4}],
                     "meta":{"tokens":120,"billed_units":1}}
                    """);
        });

        RerankProperties rerankProperties = properties();
        rerankProperties.setEnabled(true);
        rerankProperties.setApiBaseUrl(baseUrl() + "/v1");
        rerankProperties.setApiKey("rerank-key");
        EmbeddingProperties embeddingProperties = new EmbeddingProperties();

        var service = new SiliconflowRerankService(rerankProperties, embeddingProperties);
        var ranked = service.rerank("policy", List.of("doc a", "doc b"), 5);

        assertThat(authorization.get()).isEqualTo("Bearer rerank-key");
        assertThat(body.get()).contains("\"model\":\"Qwen/Qwen3-Reranker-4B\"",
                "\"query\":\"policy\"", "\"top_n\":5", "doc a", "doc b");
        assertThat(ranked).containsExactly(
                new com.aiworkmate.service.RerankService.RankedItem(1, 0.95),
                new com.aiworkmate.service.RerankService.RankedItem(0, 0.4));
    }

    @Test
    void shouldFallBackToEmbeddingApiKeyWhenRerankKeyMissing() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        server = server("/v1/rerank", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, """
                    {"id":"rerank-1","model":"Qwen/Qwen3-Reranker-4B",
                     "results":[{"index":0,"relevance_score":0.9}],
                     "meta":{"tokens":10,"billed_units":1}}
                    """);
        });

        RerankProperties rerankProperties = properties();
        rerankProperties.setEnabled(true);
        rerankProperties.setApiKey("");
        rerankProperties.setApiBaseUrl(baseUrl() + "/v1");
        EmbeddingProperties embeddingProperties = new EmbeddingProperties();
        embeddingProperties.setApiKey("embedding-key");

        var service = new SiliconflowRerankService(rerankProperties, embeddingProperties);
        service.rerank("policy", List.of("doc a"), 5);

        assertThat(authorization.get()).isEqualTo("Bearer embedding-key");
    }

    @Test
    void shouldNotBeConfiguredWhenDisabledOrKeyMissing() {
        RerankProperties rerankProperties = properties();
        rerankProperties.setEnabled(false);
        rerankProperties.setApiKey("key");
        assertThat(new SiliconflowRerankService(rerankProperties, new EmbeddingProperties()).configured())
                .isFalse();

        rerankProperties.setEnabled(true);
        rerankProperties.setApiKey("  ");
        assertThat(new SiliconflowRerankService(rerankProperties, new EmbeddingProperties()).configured())
                .isFalse();
    }

    private RerankProperties properties() {
        RerankProperties properties = new RerankProperties();
        properties.setConnectTimeoutMs(1000);
        properties.setReadTimeoutMs(3000);
        return properties;
    }

    private HttpServer server(String path, com.sun.net.httpserver.HttpHandler handler) throws Exception {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext(path, handler);
        httpServer.start();
        return httpServer;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String json) throws java.io.IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
