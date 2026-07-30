package com.onlineinterview.knowledge.domain;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class KnowledgeChunkTest {
    @Test
    void exposesStableChunkMetadataAndGuardsDocumentState() {
        var collection = KnowledgeCollection.create("owner", "Name", "Description");
        var document = KnowledgeDocument.pending(
                collection, "source.txt", "text/plain", "content");
        var chunk = KnowledgeChunk.create(document, 2, "12345");

        assertThat(chunk.getId()).isNotNull();
        assertThat(chunk.getDocument()).isSameAs(document);
        assertThat(chunk.getIndex()).isEqualTo(2);
        assertThat(chunk.getContent()).isEqualTo("12345");
        assertThat(chunk.getTokenEstimate()).isEqualTo(2);

        document.startProcessing();
        assertThatThrownBy(document::startProcessing)
                .isInstanceOf(IllegalStateException.class);
        document.markReady();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThatThrownBy(document::markReady).isInstanceOf(IllegalStateException.class);
    }
}
