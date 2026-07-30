package com.onlineinterview.knowledge.api;

import com.onlineinterview.knowledge.application.KnowledgeService;
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

    public KnowledgeController(KnowledgeService service) { this.service = service; }

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
}
