package com.example.localdocumentassistant.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryDocumentSourceRepository {

    private final Map<String, DocumentSource> sourcesByPath = new ConcurrentHashMap<>();

    public void save(DocumentSource source) {
        sourcesByPath.put(source.path(), source);
    }

    public Optional<DocumentSource> findByPath(String path) {
        return Optional.ofNullable(sourcesByPath.get(path));
    }

    public List<DocumentSource> findAll() {
        return new ArrayList<>(sourcesByPath.values());
    }
}
