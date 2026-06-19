package com.example.localdocumentassistant.processing;

import java.util.Optional;

public interface DocumentSourceRepository {

    void save(DocumentSource source);

    Optional<DocumentSource> findByPath(String path);
}
