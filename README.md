# Local Document Assistant

A Mac-first local document assistant prototype. The current app is a mocked end-to-end flow for configuring a document source, starting a fake processing job, polling status, and asking a mocked question.

## Current Status

This is still an early prototype:

- Mocked end-to-end local document assistant flow
- No real document scanning yet
- No SQLite integration yet
- No Chroma integration yet
- No Ollama integration yet
- No Apache Tika integration yet
- No embeddings, chunking, retrieval, or LLM calls yet

The app uses fake/mock data only. User-entered paths are treated as strings and are not scanned or validated.

## Tech Stack

- Backend: Java + Spring Boot
- Frontend: Vue + Vite
- APIs: mocked HTTP endpoints
- Build tools: Maven for backend, npm/Vite for frontend

## Run the Backend

Requires Java 17 or newer.

```bash
java -version
mvn -version
mvn spring-boot:run
```

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

## Current User Flow

1. Enter a document source path.
2. Click `Use this folder`.
3. The frontend sends `POST /api/processing-jobs`.
4. The backend creates a mocked in-memory processing job and returns a `jobId` and `pollUrl`.
5. Refresh or poll the mocked processing status.
6. The fake job advances over time and eventually completes.
7. Ask a mocked question.
8. The frontend sends `POST /api/questions`.
9. The UI displays a mocked answer and mocked source snippets.

## Mocked Backend Endpoints

- `POST /api/processing-jobs`
- `GET /api/processing-jobs/{jobId}`
- `POST /api/questions`

Additional mocked helper endpoints may exist for the prototype UI, such as folder examples.

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
