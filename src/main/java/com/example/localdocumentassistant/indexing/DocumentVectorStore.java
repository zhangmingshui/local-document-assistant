package com.example.localdocumentassistant.indexing;

import java.util.List;

public interface DocumentVectorStore {

    void deleteByDocumentUuid(String documentUuid);

    void store(List<EmbeddedDocumentChunk> chunks);

    List<DocumentSearchMatch> search(List<Double> queryEmbedding, int limit);
}
