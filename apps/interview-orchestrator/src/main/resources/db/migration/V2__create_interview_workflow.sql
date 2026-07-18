CREATE TABLE interview_definition (
    id UUID PRIMARY KEY,
    owner_subject VARCHAR(255) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    difficulty VARCHAR(20) NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD', 'MIXED')),
    question_mode VARCHAR(30) NOT NULL CHECK (question_mode IN ('MANUAL', 'DIRECT_LLM', 'RAG')),
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes BETWEEN 5 AND 480),
    question_count INTEGER NOT NULL CHECK (question_count BETWEEN 1 AND 100),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_interview_definition_owner ON interview_definition (owner_subject, created_at DESC);

CREATE TABLE interview_definition_skill (
    interview_definition_id UUID NOT NULL REFERENCES interview_definition(id) ON DELETE CASCADE,
    skill_order INTEGER NOT NULL,
    skill VARCHAR(100) NOT NULL,
    PRIMARY KEY (interview_definition_id, skill_order)
);

CREATE TABLE interview_assignment (
    id UUID PRIMARY KEY,
    interview_definition_id UUID NOT NULL REFERENCES interview_definition(id),
    candidate_id UUID NOT NULL REFERENCES user_profile(id),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    max_attempts INTEGER NOT NULL CHECK (max_attempts BETWEEN 1 AND 10),
    status VARCHAR(20) NOT NULL CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_assignment_window CHECK (ends_at > starts_at),
    CONSTRAINT uq_candidate_interview UNIQUE (interview_definition_id, candidate_id)
);

CREATE INDEX idx_assignment_candidate_start ON interview_assignment (candidate_id, starts_at);
