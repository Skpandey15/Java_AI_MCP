CREATE TABLE manual_question (
    id UUID PRIMARY KEY,
    interview_definition_id UUID NOT NULL REFERENCES interview_definition(id) ON DELETE CASCADE,
    question_order INTEGER NOT NULL CHECK (question_order BETWEEN 1 AND 100),
    prompt VARCHAR(4000) NOT NULL,
    max_score INTEGER NOT NULL CHECK (max_score BETWEEN 1 AND 100),
    CONSTRAINT uq_interview_question_order UNIQUE (interview_definition_id, question_order)
);

CREATE TABLE interview_session (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES interview_assignment(id),
    candidate_id UUID NOT NULL REFERENCES user_profile(id),
    state VARCHAR(30) NOT NULL CHECK (state IN ('IN_PROGRESS', 'SUBMITTED', 'EXPIRED')),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_active_assignment_session
    ON interview_session (assignment_id)
    WHERE state = 'IN_PROGRESS';
CREATE INDEX idx_session_candidate ON interview_session (candidate_id, started_at DESC);

CREATE TABLE interview_answer (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES interview_session(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES manual_question(id),
    content VARCHAR(12000) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_session_question UNIQUE (session_id, question_id)
);
