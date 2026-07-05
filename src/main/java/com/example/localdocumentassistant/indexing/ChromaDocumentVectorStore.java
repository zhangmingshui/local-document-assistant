package com.example.localdocumentassistant.indexing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Repository
public class ChromaDocumentVectorStore implements DocumentVectorStore {

    private final RestClient restClient;
    private final String tenant;
    private final String database;
    private final String collectionName;

    public ChromaDocumentVectorStore(
            RestClient.Builder restClientBuilder,
            @Value("${chroma.base-url:http://localhost:8000}") String baseUrl,
            @Value("${chroma.tenant:default_tenant}") String tenant,
            @Value("${chroma.database:default_database}") String database,
            @Value("${chroma.collection-name:local_document_chunks}") String collectionName
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.tenant = tenant;
        this.database = database;
        this.collectionName = collectionName;
    }

    @Override
    public void deleteByDocumentUuid(String documentUuid) {
        String collectionId = getOrCreateCollectionId();
        restClient.post()
                .uri(collectionRecordsPath(collectionId, "delete"), tenant, database, collectionId)
                .body(Map.of("where", Map.of("documentUuid", documentUuid)))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void store(List<EmbeddedDocumentChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }

        String collectionId = getOrCreateCollectionId();
        restClient.post()
                .uri(collectionRecordsPath(collectionId, "upsert"), tenant, database, collectionId)
                .body(Map.of(
                        "ids", chunks.stream().map(EmbeddedDocumentChunk::id).toList(),
                        "documents", chunks.stream().map(EmbeddedDocumentChunk::text).toList(),
                        "embeddings", chunks.stream().map(EmbeddedDocumentChunk::embedding).toList(),
                        "metadatas", chunks.stream().map(this::metadataMap).toList()
                ))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public List<DocumentSearchMatch> search(List<Double> queryEmbedding, int limit) {
        String collectionId = getOrCreateCollectionId();
        ChromaQueryResponse response = restClient.post()
                .uri(collectionRecordsPath(collectionId, "query"), tenant, database, collectionId)
                .body(Map.of(
                        "query_embeddings", List.of(queryEmbedding),
                        "n_results", limit,
                        "include", List.of("documents", "metadatas")
                ))
                .retrieve()
                .body(ChromaQueryResponse.class);

        if (response == null || response.documents() == null || response.documents().isEmpty()) {
            return List.of();
        }

        List<String> documents = response.documents().get(0);
        List<Map<String, Object>> metadatas = response.metadatas().get(0);
        List<DocumentSearchMatch> matches = new ArrayList<>();
        for (int index = 0; index < documents.size(); index++) {
            Map<String, Object> metadata = metadatas.get(index);
            matches.add(new DocumentSearchMatch(
                    documents.get(index),
                    stringValue(metadata, "fileName"),
                    stringValue(metadata, "filePath"),
                    intValue(metadata, "chunkIndex"),
                    longValue(metadata, "documentId"),
                    stringValue(metadata, "documentUuid")
            ));
        }
        return matches;
    }

    private String getOrCreateCollectionId() {
        ChromaCollectionResponse response = restClient.post()
                .uri("/api/v2/tenants/{tenant}/databases/{database}/collections", tenant, database)
                .body(Map.of("name", collectionName, "get_or_create", true))
                .retrieve()
                .body(ChromaCollectionResponse.class);

        if (response == null || response.id() == null || response.id().isBlank()) {
            throw new IllegalStateException("Chroma returned no collection id.");
        }
        return response.id();
    }

    private String collectionRecordsPath(String collectionId, String operation) {
        return "/api/v2/tenants/{tenant}/databases/{database}/collections/{collectionId}/" + operation;
    }

    private Map<String, Object> metadataMap(EmbeddedDocumentChunk chunk) {
        DocumentChunkMetadata metadata = chunk.metadata();
        Map<String, Object> values = new HashMap<>();
        values.put("documentId", metadata.documentId());
        values.put("documentUuid", metadata.documentUuid());
        values.put("watchedFolderId", metadata.watchedFolderId());
        values.put("fileName", metadata.fileName());
        values.put("filePath", metadata.filePath());
        values.put("fileType", metadata.fileType());
        values.put("contentHash", metadata.contentHash());
        values.put("chunkIndex", metadata.chunkIndex());
        return values;
    }

    private String stringValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? null : value.toString();
    }

    private int intValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }

    private Long longValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChromaCollectionResponse(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChromaQueryResponse(
            List<List<String>> documents,
            List<List<Map<String, Object>>> metadatas
    ) {
    }
}
