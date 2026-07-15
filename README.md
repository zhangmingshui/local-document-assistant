# Local Document Assistant

A Mac-first local document assistant prototype. The current app can configure local document sources, process supported documents, index chunks in Chroma, and answer questions using retrieved chunks with Ollama.

## Current Status

This is still an early prototype:

- SQLite stores configured folders, processing jobs, and document metadata
- Chroma stores embedded document chunks
- Ollama provides embeddings and chat responses
- Apache Tika extracts text from Word documents
- Question answering is still a thin prototype RAG flow

Source documents are read-only inputs. The app must not edit, delete, rename, move, or write into source folders.

## Tech Stack

- Backend: Java + Spring Boot
- Frontend: Vue + Vite
- APIs: Spring Boot HTTP endpoints
- Local AI: Ollama
- Vector store: Chroma
- Metadata store: SQLite
- Build tools: Maven for backend, npm/Vite for frontend

## Run the Backend

Requires Java 17 or newer.

```bash
java -version
mvn -version
mvn spring-boot:run
```

The default backend profile is `custom-ollama`, which uses the existing direct HTTP Ollama chat implementation:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=custom-ollama
```

To use the Spring AI Ollama chat implementation instead:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=spring-ai
```

Both profiles still use the existing custom Ollama embedding implementation and the existing custom Chroma integration.

If Maven reports Java 8, point it at a newer JDK first:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean install -DskipTests
```

You can also run the Spring Boot application from IntelliJ by opening the project and running `LocalDocumentAssistantApplication`.

Backend URL:

- `http://localhost:8080`

## Run the Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend URL:

- `http://localhost:5173`

The Vite dev server proxies `/api` requests to the Spring Boot backend at `http://localhost:8080`.

## Run A Local QA Benchmark

Start the local services first:

```bash
./startup-dev-services.sh
ollama pull nomic-embed-text
ollama pull qwen3:8b
```

Then start Spring Boot with the chat implementation you want to measure:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=custom-ollama
```

or:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=spring-ai
```

To explicitly ask Ollama/Qwen to return thinking diagnostics in the custom Ollama path, add:

```bash
mvn spring-boot:run \
  -Dspring-boot.run.profiles=custom-ollama \
  -Dspring-boot.run.arguments="--local-document-assistant.ollama.think=true"
```

Run the benchmark:

```bash
./scripts/qa-benchmark.sh
```

The benchmark script uses Bash and `curl`; it does not require `jq` or Python.

To repeat each question three times:

```bash
./scripts/qa-benchmark.sh 3
```

Responses are written to:

```text
data/qa-benchmark/responses.jsonl
```

Inspect the Spring Boot logs for diagnostic lines:

```bash
grep 'QA_METRICS' logs/spring-boot.log
grep 'QA_RETRIEVAL_CHUNK' logs/spring-boot.log
grep 'CHAT_MODEL_METRICS' logs/spring-boot.log
```

## RAG Settings

Normal question answering uses these backend settings:

```properties
app.rag.search-limit=8
app.rag.max-context-chunks=2
app.rag.min-relevance=0.5
```

- `app.rag.search-limit`: number of candidate chunks requested from Chroma.
- `app.rag.min-relevance`: minimum calculated relevance required before a chunk is used.
- `app.rag.max-context-chunks`: maximum number of filtered chunks passed into the LLM prompt.

`max-context-chunks` counts chunks, not documents. Multiple chunks can come from the same document.

## Current User Flow

1. Enter a document source path.
2. Click `Use this folder`.
3. The frontend sends `POST /api/processing-jobs`.
4. The backend creates a processing job and returns a `jobId` and `pollUrl`.
5. The UI polls processing status.
6. The backend scans supported documents, chunks extracted text, embeds chunks, and stores them in Chroma.
7. Ask a question.
8. The frontend sends `POST /api/questions`.
9. The backend retrieves matching chunks, calls the configured chat model, and returns an answer with source snippets.

## Backend Endpoints

- `POST /api/processing-jobs`
- `GET /api/processing-jobs/{jobId}`
- `POST /api/questions`

Additional helper endpoints may exist for the prototype UI and local debugging.

## Safety Constraints

Source files are read-only inputs.

The app must never:

- Edit source files
- Delete source files
- Rename source files
- Move source files
- Write into source folders

The current prototype does not scan folders, read real documents, validate real paths, or access the filesystem for document processing.

## Future Planned Work

Planned future layers, to be added incrementally:

- SQLite metadata
- Read-only folder validation
- Read-only document scanning
- Text extraction
- Chunking
- Chroma storage
- Ollama embeddings
- Ollama/Qwen answer generation

These are not implemented yet.
