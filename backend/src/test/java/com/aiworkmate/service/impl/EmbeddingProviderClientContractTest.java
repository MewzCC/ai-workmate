package com.aiworkmate.service.impl;

import com.aiworkmate.config.EmbeddingProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingProviderClientContractTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void localProviderShouldCallEmbedContract() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        server = server("/embed", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, """
                    {"model":"Qwen3","device":"cpu","dim":3,"count":1,
                     "normalized":true,"embeddings":[[0.1,0.2,0.3]]}
                    """);
        });
        EmbeddingProperties properties = properties();
        properties.setLocalBaseUrl(baseUrl());

        var result = new LocalEmbeddingServiceImpl(properties).embed(List.of("local query"));

        assertThat(body.get()).contains("\"texts\"", "local query");
        assertThat(result.provider()).isEqualTo("local");
        assertThat(result.vectors().get(0)).containsExactly(0.1F, 0.2F, 0.3F);
    }

    @Test
    void apiProviderShouldCallOpenAiCompatibleContract() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server = server("/v1/embeddings", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, """
                    {"model":"remote-model","data":[{"index":0,"embedding":[0.3,0.2,0.1]}]}
                    """);
        });
        EmbeddingProperties properties = properties();
        properties.setApiBaseUrl(baseUrl() + "/v1");
        properties.setApiKey("test-key");
        properties.setApiModel("remote-model");

        var result = new ApiEmbeddingProviderClient(properties).embed(List.of("remote query"));

        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        assertThat(body.get()).contains("\"model\":\"remote-model\"", "\"dimensions\":3",
                "\"input\":[\"remote query\"]");
        assertThat(result.provider()).isEqualTo("api");
        assertThat(result.vectors().get(0)).containsExactly(0.3F, 0.2F, 0.1F);
    }

    private EmbeddingProperties properties() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(3);
        properties.setBatchSize(4);
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
