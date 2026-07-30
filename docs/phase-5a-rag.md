# Phase 5A — RAG foundation

## Implemented

- Interviewer-owned knowledge collections and text/Markdown documents
- Ownership-filtered collection and document APIs
- Deterministic chunking with bounded overlap
- PostgreSQL 17 with pgvector 0.8.5 and an HNSW cosine index
- Embeddings routed `Spring Boot -> Python AI service -> LiteLLM`
- `text-embedding-3-small` exposed only through the `knowledge-embedding-model` alias
- Owner, collection, document-status and non-null-vector retrieval filters
- Citation-shaped retrieval responses containing document and chunk identifiers
- RAG interview drafts bound to one interviewer-owned knowledge collection
- Retrieved chunks supplied to question generation as untrusted, bounded context
- Model citation IDs constrained to the authorized retrieval result
- Immutable citation snapshots persisted per generated question
- Citation evidence exposed in interviewer draft and submission-review views

Documents move from `PENDING` to `PROCESSING` and become `READY` only after every
chunk embedding is stored. Search considers only `READY` documents belonging to
the authenticated interviewer.

## Quality gates

- JaCoCo enforces at least 95% line coverage across the Java knowledge package.
- PIT enforces a 65% mutation score for Phase 5 application/domain logic.
- New Python embedding modules require at least 95% line coverage.
- Flyway migrations are smoke-tested against the pinned pgvector image.

## Next

Phase 5A.4 adds retrieval-quality evaluation, configurable similarity thresholds,
and observability for grounded-generation quality.
