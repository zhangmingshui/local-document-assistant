package com.example.localdocumentassistant.ingestion;

import java.util.List;

public interface TextChunker {

    List<TextChunk> chunk(String text);
}
