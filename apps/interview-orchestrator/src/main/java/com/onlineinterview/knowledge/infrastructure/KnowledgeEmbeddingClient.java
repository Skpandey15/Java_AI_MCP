package com.onlineinterview.knowledge.infrastructure;

import java.net.http.HttpClient;
import java.util.List;
import com.onlineinterview.common.resilience.DownstreamCallExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KnowledgeEmbeddingClient {
    private final RestClient client;
    private final String serviceToken;
    private final DownstreamCallExecutor resilience;

    @Autowired
    public KnowledgeEmbeddingClient(RestClient.Builder builder,
            ObjectProvider<DownstreamCallExecutor> resilience,
            @Value("${app.ai-service.base-url}") String baseUrl,
            @Value("${app.ai-service.service-token}") String serviceToken) {
        // Force HTTP/1.1: the JDK HttpClient defaults to HTTP/2, whose cleartext handshake
        // the AI service's HTTP/1.1-only server rejects as "Invalid HTTP request received".
        // Mirrors AiQuestionClient, which already pins HTTP/1.1.
        var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.client = builder.baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
        this.serviceToken = serviceToken;
        this.resilience = resilience.getIfAvailable();
    }

    KnowledgeEmbeddingClient(RestClient.Builder builder, String baseUrl, String serviceToken) {
        this.client = builder.baseUrl(baseUrl).build();
        this.serviceToken = serviceToken;
        this.resilience = null;
    }

    public EmbeddingResponse embed(List<String> texts) {
        var call = (java.util.function.Supplier<EmbeddingResponse>) () -> client.post()
                .uri("/internal/v1/embeddings:create")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Service-Token", serviceToken)
                .body(new EmbeddingRequest(texts))
                .retrieve().body(EmbeddingResponse.class);
        return resilience == null ? call.get() : resilience.execute("ai-embedding-service", call);
    }

    public record EmbeddingRequest(List<String> texts) {}
    public record EmbeddingResponse(String modelPolicy, List<List<Double>> embeddings) {}
}
