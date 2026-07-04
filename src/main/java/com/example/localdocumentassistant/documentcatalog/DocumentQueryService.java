package com.example.localdocumentassistant.documentcatalog;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.localdocumentassistant.documentsource.DocumentSourceRepository;

@Service
public class DocumentQueryService {

    private final DocumentRepository documentRepository;
    private final DocumentSourceRepository documentSourceRepository;

    public DocumentQueryService(
            DocumentRepository documentRepository,
            DocumentSourceRepository documentSourceRepository
    ) {
        this.documentRepository = documentRepository;
        this.documentSourceRepository = documentSourceRepository;
    }

    public Optional<DocumentPage> getDocuments(Long folderId, int page, int pageSize) {
        if (documentSourceRepository.findById(folderId).isEmpty()) {
            return Optional.empty();
        }

        long totalDocuments = documentRepository.countByWatchedFolderId(folderId);
        int offset = page * pageSize;
        List<DocumentSummary> documents = documentRepository
                .findByWatchedFolderId(folderId, pageSize, offset)
                .stream()
                .map(this::toSummary)
                .toList();

        return Optional.of(new DocumentPage(
                documents,
                page,
                pageSize,
                totalDocuments,
                (long) offset + documents.size() < totalDocuments
        ));
    }

    private DocumentSummary toSummary(Document document) {
        return new DocumentSummary(
                document.id(),
                document.fileName(),
                document.filePath(),
                document.fileType(),
                document.fileSize(),
                document.lastModifiedAt(),
                document.processingStatus().name()
        );
    }

    public record DocumentPage(
            List<DocumentSummary> documents,
            int page,
            int pageSize,
            long totalDocuments,
            boolean hasMore
    ) {
    }

    public record DocumentSummary(
            Long id,
            String fileName,
            String filePath,
            String fileType,
            Long fileSize,
            String lastModifiedAt,
            String processingStatus
    ) {
    }
}
