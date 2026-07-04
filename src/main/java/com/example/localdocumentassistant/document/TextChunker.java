package com.example.localdocumentassistant.document;

import java.util.List;

public interface TextChunker {

    List<TextChunk> chunk(String text);
}
