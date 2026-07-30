CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_chunk (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES knowledge_document(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    token_estimate INTEGER NOT NULL,
    embedding vector(1536),
    CONSTRAINT uq_knowledge_chunk_position UNIQUE (document_id, chunk_index),
    CONSTRAINT chk_knowledge_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT chk_knowledge_chunk_tokens CHECK (token_estimate > 0)
);

CREATE INDEX idx_knowledge_chunk_document ON knowledge_chunk(document_id, chunk_index);
CREATE INDEX idx_knowledge_chunk_embedding_hnsw ON knowledge_chunk
    USING hnsw (embedding vector_cosine_ops) WHERE embedding IS NOT NULL;
