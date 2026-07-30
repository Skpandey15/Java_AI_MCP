package com.onlineinterview.knowledge.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineinterview.knowledge.infrastructure.KnowledgeCollectionRepository;
import com.onlineinterview.knowledge.infrastructure.KnowledgeChunkRepository;
import com.onlineinterview.knowledge.infrastructure.KnowledgeDocumentRepository;
import com.onlineinterview.knowledge.infrastructure.KnowledgeEmbeddingClient;
import com.onlineinterview.knowledge.infrastructure.KnowledgeVectorStore;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "app.ai-service.base-url=http://localhost",
        "app.ai-service.service-token=test-token"
})
@AutoConfigureMockMvc
class KnowledgeControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired KnowledgeCollectionRepository collections;
    @Autowired KnowledgeDocumentRepository documents;
    @Autowired KnowledgeChunkRepository chunks;
    @MockitoBean KnowledgeEmbeddingClient embeddingClient;
    @MockitoBean KnowledgeVectorStore vectorStore;

    @BeforeEach
    void clean() {
        chunks.deleteAll();
        documents.deleteAll();
        collections.deleteAll();
    }

    @Test
    void collectionAndDocumentLifecycle() throws Exception {
        var collectionBody = mvc.perform(post("/api/v1/knowledge/collections")
                        .with(interviewer("owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Java references","description":"Approved material"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("/api/v1/knowledge/collections/")))
                .andExpect(jsonPath("$.name").value("Java references"))
                .andReturn().getResponse().getContentAsString();
        var collectionId = json.readTree(collectionBody).get("id").asText();

        mvc.perform(get("/api/v1/knowledge/collections").with(interviewer("owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Approved material"));

        var documentBody = mvc.perform(post("/api/v1/knowledge/collections/{id}/documents", collectionId)
                        .with(interviewer("owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":"spring.md","mediaType":"text/markdown",
                                 "content":"# Spring Boot\\nTyped application framework."}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.collectionId").value(collectionId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.failureReason").isEmpty())
                .andReturn().getResponse().getContentAsString();
        var documentId = json.readTree(documentBody).get("id").asText();

        var vector = Collections.nCopies(KnowledgeVectorStore.DIMENSIONS, 0.1);
        when(embeddingClient.embed(anyList())).thenReturn(
                new KnowledgeEmbeddingClient.EmbeddingResponse("model", List.of(vector)));
        mvc.perform(post("/api/v1/knowledge/documents/{id}:ingest", documentId)
                        .with(interviewer("owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        mvc.perform(get("/api/v1/knowledge/collections/{id}/documents", collectionId)
                        .with(interviewer("owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("spring.md"))
                .andExpect(jsonPath("$[0].mediaType").value("text/markdown"));

        var hit = new KnowledgeVectorStore.SearchHit(
                UUID.randomUUID(), UUID.fromString(documentId), "spring.md", 0,
                "Typed application framework.", 0.91);
        when(vectorStore.search(eq("owner"), any(UUID.class), eq(vector), eq(2), eq(-1.0)))
                .thenReturn(List.of(hit));
        mvc.perform(post("/api/v1/knowledge/collections/{id}:search", collectionId)
                        .with(interviewer("owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Spring\",\"limit\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations[0].fileName").value("spring.md"))
                .andExpect(jsonPath("$.citations[0].score").value(0.91));

        when(vectorStore.search(eq("owner"), any(UUID.class), eq(vector), eq(8), eq(0.55)))
                .thenReturn(List.of(hit));
        mvc.perform(post("/api/v1/knowledge/collections/{id}:evaluate", collectionId)
                        .with(interviewer("owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cases":[{"query":"Spring","expectedChunkIds":["%s"]}]}
                                """.formatted(hit.chunkId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meanPrecisionAtK").value(1.0))
                .andExpect(jsonPath("$.meanRecallAtK").value(1.0))
                .andExpect(jsonPath("$.meanReciprocalRank").value(1.0))
                .andExpect(jsonPath("$.caseCount").value(1))
                .andExpect(jsonPath("$.retrievalLimit").value(8))
                .andExpect(jsonPath("$.minimumSimilarity").value(0.55));

        mvc.perform(get("/api/v1/knowledge/collections/{id}/documents", collectionId)
                        .with(interviewer("other")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidDocumentAndCandidateAccess() throws Exception {
        mvc.perform(post("/api/v1/knowledge/collections")
                        .with(interviewer("owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"description\":\"\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/knowledge/collections")
                        .with(jwt().jwt(value -> value.subject("candidate"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CANDIDATE"))))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor interviewer(
            String subject) {
        return jwt().jwt(value -> value.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_INTERVIEWER"));
    }
}
