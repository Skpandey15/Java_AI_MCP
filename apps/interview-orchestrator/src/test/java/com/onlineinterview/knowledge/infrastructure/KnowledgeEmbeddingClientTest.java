package com.onlineinterview.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KnowledgeEmbeddingClientTest {
    @Test
    void callsAuthenticatedInternalEmbeddingEndpoint() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai/internal/v1/embeddings:create"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("X-Service-Token", "token"))
                .andExpect(content().json("{\"texts\":[\"first\",\"second\"]}"))
                .andRespond(withSuccess("""
                        {"modelPolicy":"embedding-model","embeddings":[[0.1],[0.2]]}
                        """, MediaType.APPLICATION_JSON));

        var response = new KnowledgeEmbeddingClient(builder, "http://ai", "token")
                .embed(List.of("first", "second"));

        assertThat(response.modelPolicy()).isEqualTo("embedding-model");
        assertThat(response.embeddings()).containsExactly(List.of(0.1), List.of(0.2));
        server.verify();
    }
}
