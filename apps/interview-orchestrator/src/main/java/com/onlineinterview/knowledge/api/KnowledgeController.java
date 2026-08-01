package com.onlineinterview.knowledge.api;

import com.onlineinterview.knowledge.application.KnowledgeService;
import com.onlineinterview.knowledge.application.RagProperties;
import com.onlineinterview.knowledge.application.RetrievalEvaluationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/knowledge")
@PreAuthorize("hasRole('INTERVIEWER')")
public class KnowledgeController {
    private final KnowledgeService service;
    private final RetrievalEvaluationService evaluationService;
    private final RagProperties ragProperties;
    private final com.onlineinterview.knowledge.infrastructure.TikaTextExtractor extractor;

    public KnowledgeController(KnowledgeService service,
            RetrievalEvaluationService evaluationService, RagProperties ragProperties,
            com.onlineinterview.knowledge.infrastructure.TikaTextExtractor extractor) {
        this.service = service;
        this.evaluationService = evaluationService;
        this.ragProperties = ragProperties;
        this.extractor = extractor;
    }

    @PostMapping("/collections/{collectionId}/documents:upload")
    public ResponseEntity<DocumentResponse> upload(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID collectionId,
            @org.springframework.web.bind.annotation.RequestParam("file")
            org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        String text = extractor.extract(file.getBytes());
        if (text == null || text.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "No extractable text found in the uploaded file");
        }
        var fileName = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        var value = service.addDocument(jwt.getSubject(), collectionId, fileName, "text/plain", text);
        return ResponseEntity.accepted().location(URI.create(
                "/api/v1/knowledge/documents/" + value.getId())).body(DocumentResponse.from(value));
    }

    @PostMapping("/collections")
    public ResponseEntity<CollectionResponse> createCollection(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateCollectionRequest request) {
        var value = service.createCollection(jwt.getSubject(), request.name(), request.description());
        return ResponseEntity.created(URI.create("/api/v1/knowledge/collections/" + value.getId()))
                .body(CollectionResponse.from(value));
    }

    @GetMapping("/collections")
    public List<CollectionResponse> listCollections(@AuthenticationPrincipal Jwt jwt) {
        return service.listCollections(jwt.getSubject()).stream()
                .map(CollectionResponse::from).toList();
    }

    @PostMapping("/collections/{collectionId}/documents")
    public ResponseEntity<DocumentResponse> addDocument(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID collectionId,
            @Valid @RequestBody CreateDocumentRequest request) {
        var value = service.addDocument(jwt.getSubject(), collectionId,
                request.fileName(), request.mediaType(), request.content());
        return ResponseEntity.accepted().location(URI.create(
                "/api/v1/knowledge/documents/" + value.getId())).body(DocumentResponse.from(value));
    }

    @GetMapping("/collections/{collectionId}/documents")
    public List<DocumentResponse> listDocuments(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID collectionId) {
        return service.listDocuments(jwt.getSubject(), collectionId).stream()
                .map(DocumentResponse::from).toList();
    }

    @PostMapping("/documents/{documentId}:prepare")
    public DocumentResponse prepareDocument(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId) {
        return DocumentResponse.from(service.prepareDocument(jwt.getSubject(), documentId));
    }

    @PostMapping("/documents/{documentId}:ingest")
    public DocumentResponse ingestDocument(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId) {
        return DocumentResponse.from(service.ingestDocument(jwt.getSubject(), documentId));
    }

    @PostMapping("/collections/{collectionId}:search")
    public KnowledgeSearchResponse search(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID collectionId,
            @Valid @RequestBody SearchKnowledgeRequest request) {
        return KnowledgeSearchResponse.from(service.search(
                jwt.getSubject(), collectionId, request.query(), request.limit()));
    }

    @PostMapping("/collections/{collectionId}:evaluate")
    public RetrievalEvaluationResponse evaluate(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID collectionId,
            @Valid @RequestBody EvaluateRetrievalRequest request) {
        return RetrievalEvaluationResponse.from(evaluationService.evaluate(
                jwt.getSubject(), collectionId, request.cases()),
                ragProperties.getRetrievalLimit(), ragProperties.getMinimumSimilarity());
    }
}
