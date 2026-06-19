# AGENTS.md

## Project overview

This is a Mac-first local document assistant.

The long-term goal is:
- Scan a user-configured local folder of documents.
- Extract text from supported documents.
- Chunk extracted text.
- Embed chunks using Ollama with `nomic-embed-text`.
- Store chunk text, embeddings, and metadata in Chroma.
- Use Ollama with a local Qwen 7B/8B model to answer questions from retrieved chunks.

## Current architecture

- Backend: Java + Spring Boot.
- Frontend: Vue + Vite.
- Spring Boot should eventually serve the built Vue frontend.
- SQLite will eventually store app metadata and processing state.
- Chroma will eventually store chunks, embeddings, and chunk metadata.
- Ollama will eventually run local embedding and LLM models.
- Source files are read-only inputs.

## Development approach

Build incrementally in small, reviewable slices.

Do not implement future layers until the current task explicitly asks for them.

Prefer simple mocked behaviour before real integration.

Each task should be easy to review, run, and commit.

## Important safety constraints

The app must never edit, delete, rename, move, or modify source documents.

Do not add code that writes to source folders.

Do not add code that moves, deletes, or renames user files.

Do not scan folders or read real documents unless the current task explicitly asks for it.

When file paths are needed before real filesystem integration, use mocked paths or user-entered strings only.

## Do not add unless explicitly requested

Do not add these technologies or integrations unless the current task explicitly asks for them:

- SQLite
- Chroma
- Ollama
- Apache Tika
- embeddings
- chunking
- document processing
- LLM calls
- Docker
- authentication
- packaging
- background schedulers
- filesystem scanning

## Current prototype behaviour

The app currently uses mocked data.

The document source UI may send mocked or simple HTTP requests to the backend.

The processing job endpoints may return dummy job/status JSON.

The ask/answer UI may show mocked answers and mocked source snippets.

## Backend conventions

Keep Spring Boot changes small and straightforward.

Use simple controllers and Java records for request/response models where appropriate.

Do not add new backend dependencies unless the task explicitly requires them.

Avoid introducing persistence or background processing until requested.

## Frontend conventions

Keep the Vue frontend simple.

Do not add Vue Router, Pinia, or UI libraries unless explicitly requested.

Prefer simple component state and props for now.

Keep API calls visible and easy to understand.

Show simple loading and error states for HTTP requests.

## Validation before finishing a task

Before considering a task complete:

- Check that the backend still starts.
- Check that the frontend still starts.
- Check that the changed UI flow works manually.
- Check that no forbidden technology or filesystem behaviour was added.
- Keep the diff small and reviewable.
