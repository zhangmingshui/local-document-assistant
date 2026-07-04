package com.example.localdocumentassistant.document;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class SimpleCharacterTextChunker implements TextChunker {

    private static final int DEFAULT_CHUNK_SIZE = 1_000;

    private final int chunkSize;

    public SimpleCharacterTextChunker() {
        this(DEFAULT_CHUNK_SIZE);
    }

    public SimpleCharacterTextChunker(int chunkSize) {
        if (chunkSize < 1) {
            throw new IllegalArgumentException("Chunk size must be greater than zero.");
        }
        this.chunkSize = chunkSize;
    }

    @Override
    public List<TextChunk> chunk(String text) {
        List<TextChunk> chunks = new ArrayList<>();
        for (int start = 0, chunkIndex = 0; start < text.length(); start += chunkSize, chunkIndex++) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(new TextChunk(chunkIndex, text.substring(start, end)));
        }
        return chunks;
    }
}
