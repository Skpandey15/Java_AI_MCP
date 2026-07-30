CREATE TABLE knowledge_collection (
    id UUID PRIMARY KEY,
    owner_subject VARCHAR(255) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_knowledge_collection_owner_name UNIQUE (owner_subject, name)
);

CREATE TABLE knowledge_document (
    id UUID PRIMARY KEY,
    collection_id UUID NOT NULL REFERENCES knowledge_collection(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_knowledge_document_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED'))
);

CREATE INDEX idx_knowledge_collection_owner ON knowledge_collection(owner_subject);
CREATE INDEX idx_knowledge_document_collection ON knowledge_document(collection_id, created_at);
