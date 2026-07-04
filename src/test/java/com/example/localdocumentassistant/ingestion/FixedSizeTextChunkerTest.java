package com.example.localdocumentassistant.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FixedSizeTextChunkerTest {

    @Test
    void splitsTextIntoIndexedCharacterChunks() {
        FixedSizeTextChunker chunker = new FixedSizeTextChunker(4);

        assertThat(chunker.chunk("abcdefghij"))
                .containsExactly(
                        new TextChunk(0, "abcd"),
                        new TextChunk(1, "efgh"),
                        new TextChunk(2, "ij")
                );
    }
}
