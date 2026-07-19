ALTER TABLE interview_session
    ADD COLUMN review_status VARCHAR(30) NOT NULL DEFAULT 'NOT_SUBMITTED',
    ADD COLUMN objective_score INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_score INTEGER,
    ADD COLUMN review_feedback VARCHAR(4000),
    ADD COLUMN reviewed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN reviewer_subject VARCHAR(255);

ALTER TABLE interview_session
    ADD CONSTRAINT chk_review_status
    CHECK (review_status IN ('NOT_SUBMITTED', 'PENDING_REVIEW', 'REVIEWED'));

UPDATE interview_session
SET review_status = 'PENDING_REVIEW'
WHERE state = 'SUBMITTED';

ALTER TABLE interview_answer
    ADD COLUMN awarded_score INTEGER,
    ADD COLUMN reviewer_feedback VARCHAR(4000),
    ADD COLUMN auto_scored BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE interview_answer
    ADD CONSTRAINT chk_awarded_score_non_negative
    CHECK (awarded_score IS NULL OR awarded_score >= 0);

CREATE INDEX idx_session_review_queue
    ON interview_session (review_status, submitted_at DESC);
