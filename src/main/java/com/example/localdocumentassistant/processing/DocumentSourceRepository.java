package com.example.localdocumentassistant.processing;

import java.util.List;
import java.util.Optional;

public interface DocumentSourceRepository {

    DocumentSource create(DocumentSource source);

    Optional<DocumentSource> findByPath(String path);

    List<DocumentSource> findAll();
}
