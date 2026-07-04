package com.example.localdocumentassistant.source;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class DocumentSourceService {

    private final DocumentSourceRepository documentSourceRepository;

    public DocumentSourceService(DocumentSourceRepository documentSourceRepository) {
        this.documentSourceRepository = documentSourceRepository;
    }

    public List<DocumentSourceSummary> getConfiguredSources() {
        return documentSourceRepository.findAll().stream()
                .sorted(Comparator.comparing(DocumentSource::path))
                .map(source -> new DocumentSourceSummary(
                        source.id(),
                        source.path(),
                        0,
                        source.includeSubfolders(),
                        source.status()
                ))
                .toList();
    }

    public record DocumentSourceSummary(
            Long id,
            String path,
            int documentCount,
            boolean includeSubfolders,
            String status
    ) {
    }
}
