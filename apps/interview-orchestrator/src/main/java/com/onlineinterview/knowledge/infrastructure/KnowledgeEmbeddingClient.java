package com.onlineinterview.knowledge.infrastructure;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KnowledgeEmbeddingClient {
    private final RestClient client;
    private final String serviceToken;

    @Autowired
    public KnowledgeEmbeddingClient(RestClient.Builder builder,
            @Value("${app.ai-service.base-url}") String baseUrl,
            @Value("${app.ai-service.service-token}") String serviceToken) {
        this.client = builder.baseUrl(baseUrl).build();
        this.serviceToken = serviceToken;
    }

    public EmbeddingResponse embed(List<String> texts) {
        return client.post().uri("/internal/v1/embeddings:create")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Service-Token", serviceToken)
                .body(new EmbeddingRequest(texts))
                .retrieve().body(EmbeddingResponse.class);
    }

    public record EmbeddingRequest(List<String> texts) {}
    public record EmbeddingResponse(String modelPolicy, List<List<Double>> embeddings) {}
}
