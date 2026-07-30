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
- Configurable cosine-similarity floor and retrieval limit for live RAG generation
- Authenticated retrieval evaluation with precision@K, recall@K and mean reciprocal rank
- Prometheus metrics for retrieval latency, hit count, similarity, citation density,
  citation similarity, generation failures and evaluation quality

Documents move from `PENDING` to `PROCESSING` and become `READY` only after every
chunk embedding is stored. Search considers only `READY` documents belonging to
the authenticated interviewer.

## Quality gates

- JaCoCo enforces at least 95% line coverage across the Java knowledge package.
- PIT enforces a 65% mutation score for Phase 5 application/domain logic.
- New Python embedding modules require at least 95% line coverage.
- Flyway migrations are smoke-tested against the pinned pgvector image.

## Next

Phase 5A.4 is implemented. Tune `RAG_MINIMUM_SIMILARITY` and
`RAG_RETRIEVAL_LIMIT` using a representative evaluation set rather than changing
the defaults without measurement.

Evaluation endpoint:

`POST /api/v1/knowledge/collections/{collectionId}:evaluate`

Each case supplies a query and the chunk IDs expected to be relevant. The result
reports mean precision@K, recall@K and reciprocal rank using the same threshold
and limit as live generation.
