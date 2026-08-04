-- AI candidate-answer-aware "detailed answer" for a single wrong/partial answer: explains
-- why THAT candidate's submitted answer fell short and how to reach the correct answer.
-- Keyed by answer (not question) because the content critiques one candidate's specific
-- answer, so it must never be reused across candidates. Managed via JdbcTemplate, so it is
-- not a JPA entity and does not participate in Hibernate schema validation before migration.
CREATE TABLE answer_explanation (
    answer_id UUID PRIMARY KEY REFERENCES interview_answer(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
