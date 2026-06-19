package com.example.localdocumentassistant.processing;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryDocumentSourceRepository implements DocumentSourceRepository {

    private final Map<String, DocumentSource> sourcesByPath = new ConcurrentHashMap<>();

    @Override
    public void save(DocumentSource source) {
        sourcesByPath.put(source.path(), source);
    }

    @Override
    public Optional<DocumentSource> findByPath(String path) {
        return Optional.ofNullable(sourcesByPath.get(path));
    }
}
