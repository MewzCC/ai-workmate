package com.aiworkmate.service.impl;

import com.aiworkmate.config.IntegrationUpstreamProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultIntegrationSandboxClientTest {
 private HttpServer server;
 @AfterEach void stop(){if(server!=null)server.stop(0);}
 @Test void unavailableByDefaultAndNeverExposesBaseUrl(){IntegrationUpstreamProperties p=new IntegrationUpstreamProperties();IntegrationUpstreamProperties.Upstream u=new IntegrationUpstreamProperties.Upstream();u.setDisplayName("Sandbox");u.setBaseUrl("https://secret.internal.example/v1");p.setUpstreams(Map.of("sandbox",u));DefaultIntegrationSandboxClient client=new DefaultIntegrationSandboxClient(p);assertThat(client.options()).singleElement().satisfies(o->{assertThat(o.label()).isEqualTo("Sandbox");assertThat(o.available()).isFalse();assertThat(o.toString()).doesNotContain("secret.internal");});}
 @Test void rejectsProtocolRelativePathAtClientBoundary(){IntegrationUpstreamProperties p=properties(6553);var result=new DefaultIntegrationSandboxClient(p).execute("sandbox","GET","//evil.invalid/x",null);assertThat(result.errorCode()).isEqualTo("TARGET_REJECTED");}
 @Test void callsOnlyConfiguredOriginAndDoesNotFollowRedirects()throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/base/check",exchange->{byte[] body="ok".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(302,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();IntegrationUpstreamProperties p=properties(server.getAddress().getPort());DefaultIntegrationSandboxClient client=new DefaultIntegrationSandboxClient(p);var result=client.execute("sandbox","GET","/check",null);assertThat(result.httpStatus()).isEqualTo(302);assertThat(result.outcome()).isEqualTo("FAILED");assertThat(result.errorCode()).isEqualTo("UPSTREAM_HTTP_ERROR");}
 @Test void rejectsOversizedResponse()throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/base/large",exchange->{byte[] body="x".repeat(2048).getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();IntegrationUpstreamProperties p=properties(server.getAddress().getPort());p.setMaxResponseBytes(1024);var result=new DefaultIntegrationSandboxClient(p).execute("sandbox","GET","/large",null);assertThat(result.errorCode()).isEqualTo("RESPONSE_TOO_LARGE");assertThat(result.responsePreview()).isNull();}
 private IntegrationUpstreamProperties properties(int port){IntegrationUpstreamProperties p=new IntegrationUpstreamProperties();IntegrationUpstreamProperties.Upstream u=new IntegrationUpstreamProperties.Upstream();u.setDisplayName("Sandbox");u.setBaseUrl("http://127.0.0.1:"+port+"/base");u.setEnabled(true);u.setSandbox(true);u.setAllowInsecureHttp(true);p.setUpstreams(Map.of("sandbox",u));return p;}
}
