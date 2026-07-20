# Local Document Assistant Backend Architecture Summary

This document summarizes the current backend code structure for the local document assistant prototype.

## Current Backend Shape

The backend is organized mostly by feature/domain, with JDBC persistence separated into a `persistence` package.

Relevant production tree:

```text
src/main/java/com/example/localdocumentassistant
├── api
│   └── LocalDocumentAssistantController.java
├── documentcatalog
│   ├── Document.java
│   ├── DocumentProcessingStatus.java
│   ├── DocumentQueryService.java
│   └── DocumentRepository.java
├── documentsource
│   ├── DocumentSource.java
│   ├── DocumentSourceRepository.java
│   ├── DocumentSourceService.java
│   ├── DuplicateDocumentSourceException.java
│   └── InMemoryDocumentSourceRepository.java
├── indexing
│   ├── ChromaDocumentVectorStore.java
│   ├── DocumentChunkMetadata.java
│   ├── DocumentIndexingService.java
│   ├── DocumentSearchMatch.java
│   ├── DocumentSearchService.java
│   ├── DocumentVectorStore.java
│   ├── EmbeddedDocumentChunk.java
│   ├── EmbeddingService.java
│   └── OllamaEmbeddingService.java
├── ingestion
│   ├── DiscoveredDocumentMetadata.java
│   ├── DocumentIngestionRunner.java
│   ├── DocumentInventoryService.java
│   ├── DocumentProcessingOutcome.java
│   ├── DocumentTextExtractor.java
│   ├── DocumentTextProcessingService.java
│   ├── FixedSizeTextChunker.java
│   ├── IngestionJob.java
│   ├── IngestionJobRepository.java
│   ├── IngestionJobService.java
│   ├── IngestionJobStatus.java
│   ├── PlainTextExtractor.java
│   ├── TextChunk.java
│   ├── TextChunker.java
│   └── TikaDocumentTextExtractor.java
├── persistence
│   ├── JdbcDocumentRepository.java
│   ├── JdbcDocumentSourceRepository.java
│   ├── JdbcIngestionJobRepository.java
│   └── SqliteDatabaseInitializer.java
└── questionanswering
    ├── ChatModelService.java
    ├── ChatModelUnavailableException.java
    ├── OllamaChatModelService.java
    ├── QuestionAnsweringResult.java
    ├── QuestionAnsweringService.java
    └── QuestionSource.java
```

Relevant test tree:

```text
src/test/java/com/example/localdocumentassistant
├── api
│   └── LocalDocumentAssistantControllerTest.java
├── indexing
│   ├── DocumentIndexingServiceTest.java
│   └── DocumentSearchServiceTest.java
├── ingestion
│   ├── DocumentIngestionRunnerTest.java
│   ├── DocumentInventoryServiceTest.java
│   ├── DocumentTextProcessingServiceTest.java
│   ├── FixedSizeTextChunkerTest.java
│   ├── IngestionJobServiceTest.java
│   ├── PlainTextExtractorTest.java
│   └── TikaDocumentTextExtractorTest.java
└── questionanswering
    ├── OllamaChatModelServiceTest.java
    └── QuestionAnsweringServiceTest.java
```

## Document Processing

Document processing mainly lives in the `ingestion` package.

`IngestionJobService` starts processing jobs. It creates or reuses configured document sources, creates an `IngestionJob`, and starts `DocumentIngestionRunner`.

`DocumentIngestionRunner` is the main processing orchestrator. It:

- scans configured folders
- discovers supported files
- updates job status and progress
- delegates inventory persistence to `DocumentInventoryService`
- finds documents that need processing
- runs each document through `DocumentTextProcessingService`

`DocumentInventoryService` owns discovered-document persistence and change detection. It:

- checks existing documents by `watchedFolderId + filePath`
- calculates SHA-256 hashes
- marks new or changed files as `NEEDS_PROCESSING`
- avoids duplicate document rows

`DocumentTextProcessingService` owns per-document text processing. It:

- selects a `DocumentTextExtractor`
- extracts text with a timeout
- chunks text through `TextChunker`
- delegates embedding and Chroma indexing to `DocumentIndexingService`
- marks documents with no extractable text when appropriate

Current extractors:

- `PlainTextExtractor` for `.txt`
- `TikaDocumentTextExtractor` for `.doc` and `.docx`

Current chunker:

- `FixedSizeTextChunker`

## Chroma And Vector Storage

Vector storage and embedding code lives in the `indexing` package.

`DocumentIndexingService` embeds document chunks and stores them in Chroma. Before writing new chunks, it deletes existing Chroma chunks for the same `documentUuid`, so reprocessing replaces old embeddings for that document.

`EmbeddingService` is the abstraction for embedding generation.

`OllamaEmbeddingService` implements `EmbeddingService` using direct HTTP calls to Ollama:

```text
POST {ollama.base-url}/api/embed
```

Current defaults:

```properties
ollama.base-url=http://localhost:11434
ollama.embedding-model=nomic-embed-text
```

`DocumentVectorStore` abstracts vector storage and vector search.

`ChromaDocumentVectorStore` implements `DocumentVectorStore`. It:

- creates or gets the Chroma collection
- upserts embedded chunks
- deletes chunks by `documentUuid`
- searches Chroma with a query embedding
- maps Chroma documents, metadata, and distances into `DocumentSearchMatch`

`DocumentSearchMatch` exposes both Chroma distance and calculated relevance:

```text
relevance = 1.0 / (1.0 + distance)
```

The relevance value is a friendlier debug/display value. Filtering currently uses relevance.

## Search And Question Answering

Search and question answering are split between `indexing` and `questionanswering`.

`DocumentSearchService` embeds a search query using `EmbeddingService`, then searches Chroma through `DocumentVectorStore`.

`QuestionAnsweringService` coordinates the RAG flow:

1. Calls `DocumentSearchService.search(...)`.
2. Filters matches using configured minimum relevance.
3. Limits the number of context chunks.
4. Builds the prompt.
5. Calls `ChatModelService`.
6. Returns the answer plus source metadata.

Current RAG settings:

```properties
app.rag.search-limit=8
app.rag.max-context-chunks=3
app.rag.min-relevance=0.5
```

If no chunks pass the relevance filter, `QuestionAnsweringService` returns:

```text
I could not find relevant information in the indexed documents.
```

In that case it does not call the chat model.

## Prompt Construction

Prompts are currently built inside `QuestionAnsweringService`, in the private `buildPrompt(...)` method.

The prompt tells the model:

- answer only using the supplied context
- if the answer is not in the context, say it could not find relevant information in the indexed documents

The prompt includes source labels around retrieved chunks, for example:

```text
[Source 1: notes.txt, chunk 0]
...
```

The backend builds the source list from retrieved Chroma metadata. The model is not asked to invent or choose sources.

## Ollama Calls

There are two direct Ollama integrations.

`OllamaEmbeddingService` calls:

```text
POST {ollama.base-url}/api/embed
```

It uses the configured embedding model, currently:

```properties
ollama.embedding-model=nomic-embed-text
```

`OllamaChatModelService` calls:

```text
POST {ollama.base-url}/api/chat
```

It sends a non-streaming chat request:

```json
{
  "model": "qwen2.5:3b",
  "stream": false,
  "messages": [
    {
      "role": "user",
      "content": "..."
    }
  ]
}
```

Current chat model default:

```properties
ollama.chat-model=qwen2.5:3b
```

## Controllers, DTOs, And Endpoints

There is currently one main REST controller:

```text
api/LocalDocumentAssistantController.java
```

Current endpoints:

```text
GET  /api/folders
GET  /api/folders/{folderId}/documents
GET  /api/processing-jobs/latest
POST /api/processing-jobs
POST /api/folders/{folderId}/processing-jobs
GET  /api/processing-jobs/{jobId}
POST /api/search
POST /api/questions
GET  /api/dev/chroma-collections
```

Most HTTP DTOs are nested records inside `LocalDocumentAssistantController`:

```text
ProcessingJobResponse
StartProcessingJobRequest
StartProcessingJobResponse
QuestionRequest
QuestionResponse
SourceResponse
SearchRequest
SearchResponse
SearchResultResponse
ErrorResponse
```

`SearchResultResponse` currently includes:

- text
- fileName
- filePath
- chunkIndex
- documentId
- documentUuid
- distance
- relevance

## Responsibility Separation

The broad package separation is reasonably clear:

```text
api               HTTP routes and DTO mapping
documentsource    configured folders
documentcatalog   document metadata and document querying
ingestion         scanning, inventory, extraction, chunking, job orchestration
indexing          embeddings, Chroma storage, vector search
questionanswering RAG flow and chat model calls
persistence       SQLite/JdbcTemplate implementations
```

Some classes are more central or mixed:

`DocumentIngestionRunner` is the largest central processing class. It handles scanning, progress updates, failure handling, orchestration, and the document-processing loop.

`LocalDocumentAssistantController` owns all API routes and most HTTP DTOs. It covers folders, processing jobs, documents, search, questions, and a dev Chroma endpoint.

`QuestionAnsweringService` combines retrieval, relevance filtering, retrieval logging, prompt construction, chat invocation, timing logs, and result shaping.

`ChromaDocumentVectorStore` combines Chroma collection management, chunk storage, deletion, search execution, and response mapping.

`DocumentTextProcessingService` combines extractor selection, timeout handling, empty-text handling, chunking, indexing, and status updates for no-extractable-text cases.

## Existing Abstractions For Future Work

Useful abstractions already exist:

```text
DocumentTextExtractor
TextChunker
EmbeddingService
DocumentVectorStore
ChatModelService
DocumentRepository
DocumentSourceRepository
IngestionJobRepository
```

These abstractions support future extension points:

- `DocumentTextExtractor` allows more file extractors to be added later.
- `TextChunker` allows chunking strategy changes.
- `EmbeddingService` isolates Ollama embedding calls.
- `DocumentVectorStore` isolates Chroma.
- `ChatModelService` isolates Ollama chat calls.
- `IngestionJobService` and `IngestionJobRepository` provide a task/job-style processing model.
- `DocumentSearchMatch` and `QuestionSource` already carry source metadata for citations.

There is not currently a dedicated abstraction for chat sessions, multi-step assistant workflows, or tool-calling. The closest foundations are `ChatModelService` for model calls and `IngestionJob` for task-style background work.
