ALTER TABLE interview_definition
    ADD COLUMN knowledge_collection_id UUID REFERENCES knowledge_collection(id);

ALTER TABLE manual_question DROP CONSTRAINT chk_question_source;
ALTER TABLE manual_question
    ADD CONSTRAINT chk_question_source CHECK (source IN ('MANUAL', 'AI_DIRECT', 'AI_RAG'));

CREATE TABLE question_citation (
    question_id UUID NOT NULL REFERENCES manual_question(id) ON DELETE CASCADE,
    citation_order INTEGER NOT NULL,
    chunk_id UUID NOT NULL REFERENCES knowledge_chunk(id),
    document_id UUID NOT NULL REFERENCES knowledge_document(id),
    file_name VARCHAR(255) NOT NULL,
    chunk_index INTEGER NOT NULL,
    excerpt VARCHAR(2000) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (question_id, citation_order),
    CONSTRAINT chk_question_citation_score CHECK (score >= -1 AND score <= 1)
);

CREATE INDEX idx_question_citation_chunk ON question_citation(chunk_id);
