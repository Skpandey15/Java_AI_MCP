package com.onlineinterview.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentChunkerTest {
    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void normalizesAndKeepsShortTextTogether() {
        assertThat(chunker.chunk("  first\r\n\r\nsecond  "))
                .containsExactly("first\n\nsecond");
        assertThat(chunker.chunk(" \r\n ")).isEmpty();
    }

    @Test
    void splitsLongTextWithBoundedOverlapAtNaturalBoundary() {
        var paragraph = "a".repeat(900) + ". " + "b".repeat(900) + "\n\n" + "c".repeat(900);
        var chunks = chunker.chunk(paragraph);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(value ->
                assertThat(value.length()).isLessThanOrEqualTo(DocumentChunker.MAX_CHARS));
        assertThat(chunks.get(1)).contains("b");
    }
}
