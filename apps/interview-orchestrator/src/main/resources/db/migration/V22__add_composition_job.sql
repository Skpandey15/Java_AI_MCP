-- Async composition jobs: the agentic compose loop runs in the background instead of
-- inside the HTTP request, so long compositions never time out. Managed via JdbcTemplate
-- (auto-committing status updates), so it is not a JPA entity.
CREATE TABLE composition_job (
    id UUID PRIMARY KEY,
    interview_definition_id UUID NOT NULL,
    owner_subject VARCHAR(255) NOT NULL,
    generation_request_id UUID NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    question_count INT NOT NULL DEFAULT 0,
    rounds INT NOT NULL DEFAULT 0,
    error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_composition_job_owner ON composition_job (owner_subject, created_at DESC);
