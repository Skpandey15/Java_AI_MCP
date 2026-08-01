-- AI-generated "answer key" per question (correct answer with details + example).
-- Keyed by question so it is generated once and reused for every candidate who
-- takes the interview. Managed via JdbcTemplate, so it is not a JPA entity.
CREATE TABLE question_model_answer (
    question_id UUID PRIMARY KEY REFERENCES manual_question(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
