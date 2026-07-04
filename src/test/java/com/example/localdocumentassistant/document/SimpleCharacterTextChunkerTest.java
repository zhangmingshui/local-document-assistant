package com.example.localdocumentassistant.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimpleCharacterTextChunkerTest {

    @Test
    void splitsTextIntoIndexedCharacterChunks() {
        SimpleCharacterTextChunker chunker = new SimpleCharacterTextChunker(4);

        assertThat(chunker.chunk("abcdefghij"))
                .containsExactly(
                        new TextChunk(0, "abcd"),
                        new TextChunk(1, "efgh"),
                        new TextChunk(2, "ij")
                );
    }
}
