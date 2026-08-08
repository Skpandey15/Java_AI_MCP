-- Allow the new ADAPTIVE question mode.
ALTER TABLE interview_definition DROP CONSTRAINT interview_definition_question_mode_check;
ALTER TABLE interview_definition ADD CONSTRAINT interview_definition_question_mode_check
    CHECK (question_mode IN ('MANUAL', 'DIRECT_LLM', 'RAG', 'ADAPTIVE'));

-- Per-session adaptive state (1:1 with interview_session when the interview is ADAPTIVE).
CREATE TABLE adaptive_session_state (
    session_id UUID PRIMARY KEY REFERENCES interview_session(id) ON DELETE CASCADE,
    turns_used INTEGER NOT NULL,
    max_turns INTEGER NOT NULL,
    tokens_used INTEGER NOT NULL,
    token_budget INTEGER NOT NULL,
    phase VARCHAR(20) NOT NULL CHECK (phase IN ('RUNNING', 'DONE'))
);

-- One row per asked question; answer/score fill in when the candidate responds.
CREATE TABLE adaptive_turn (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES interview_session(id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL,
    question_id UUID,
    question_text TEXT NOT NULL,
    skill VARCHAR(200) NOT NULL,
    difficulty VARCHAR(40) NOT NULL,
    source VARCHAR(20) NOT NULL CHECK (source IN ('BANK', 'GENERATED')),
    answer_text TEXT,
    score INTEGER,
    confidence INTEGER,
    agent_rationale TEXT,
    UNIQUE (session_id, ordinal)
);

CREATE INDEX idx_adaptive_turn_session ON adaptive_turn(session_id, ordinal);
