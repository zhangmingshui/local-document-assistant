package com.example.localdocumentassistant.processing;

import java.util.List;
import java.util.Optional;

public interface DocumentSourceRepository {

    Optional<DocumentSource> findByPath(String path);

    List<DocumentSource> findAll();
}
