ALTER TABLE manual_question
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN generation_request_id UUID,
    ADD COLUMN model_policy VARCHAR(200),
    ADD COLUMN prompt_version VARCHAR(100);

ALTER TABLE manual_question
    ADD CONSTRAINT chk_question_source CHECK (source IN ('MANUAL', 'AI_DIRECT'));

CREATE UNIQUE INDEX uq_generation_request_order
    ON manual_question (generation_request_id, question_order)
    WHERE generation_request_id IS NOT NULL;
