ALTER TABLE interview_definition
    ADD COLUMN passing_percentage INTEGER NOT NULL DEFAULT 70;

ALTER TABLE interview_definition
    ADD CONSTRAINT chk_passing_percentage
    CHECK (passing_percentage BETWEEN 1 AND 100);

ALTER TABLE interview_session
    ADD COLUMN result_outcome VARCHAR(30);

UPDATE interview_session
SET result_outcome = 'NOT_SELECTED'
WHERE review_status = 'REVIEWED';

UPDATE interview_session session
SET result_outcome = 'PASSED'
FROM interview_assignment assignment
JOIN interview_definition definition ON definition.id = assignment.interview_definition_id
JOIN (
    SELECT interview_definition_id, SUM(max_score) AS max_score
    FROM manual_question
    GROUP BY interview_definition_id
) question_totals ON question_totals.interview_definition_id = definition.id
WHERE session.assignment_id = assignment.id
  AND session.review_status = 'REVIEWED'
  AND session.total_score IS NOT NULL
  AND question_totals.max_score > 0;

ALTER TABLE interview_session
    ADD CONSTRAINT chk_result_outcome
    CHECK (result_outcome IS NULL OR result_outcome IN ('PASSED', 'NOT_SELECTED')),
    ADD CONSTRAINT chk_objective_score_non_negative
    CHECK (objective_score >= 0),
    ADD CONSTRAINT chk_total_score_non_negative
    CHECK (total_score IS NULL OR total_score >= 0),
    ADD CONSTRAINT chk_review_release_consistency
    CHECK (
        (review_status = 'REVIEWED'
            AND total_score IS NOT NULL
            AND result_outcome IS NOT NULL
            AND reviewed_at IS NOT NULL
            AND reviewer_subject IS NOT NULL)
        OR
        (review_status <> 'REVIEWED' AND result_outcome IS NULL)
    );

CREATE TABLE review_audit_event (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES interview_session(id),
    answer_id UUID REFERENCES interview_answer(id),
    event_type VARCHAR(40) NOT NULL,
    actor_subject VARCHAR(255) NOT NULL,
    awarded_score INTEGER,
    feedback VARCHAR(4000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_review_audit_event_type
        CHECK (event_type IN ('ANSWER_SCORED', 'REVIEW_FINALIZED'))
);

CREATE INDEX idx_review_audit_session_time
    ON review_audit_event (session_id, occurred_at);
