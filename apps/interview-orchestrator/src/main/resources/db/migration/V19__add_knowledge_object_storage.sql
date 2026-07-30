ALTER TABLE knowledge_document
    ALTER COLUMN content DROP NOT NULL,
    ADD COLUMN object_key VARCHAR(1024),
    ADD COLUMN object_size BIGINT,
    ADD COLUMN content_sha256 VARCHAR(64);

ALTER TABLE knowledge_document
    ADD CONSTRAINT uq_knowledge_document_object_key UNIQUE (object_key),
    ADD CONSTRAINT chk_knowledge_document_source CHECK (
        (content IS NOT NULL AND object_key IS NULL)
        OR
        (content IS NULL AND object_key IS NOT NULL
            AND object_size > 0
            AND content_sha256 ~ '^[0-9a-f]{64}$')
    );
