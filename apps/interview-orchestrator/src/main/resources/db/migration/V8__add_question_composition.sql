ALTER TABLE interview_definition
    ADD COLUMN mcq_single_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN mcq_multiple_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN short_text_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN long_text_count INTEGER;

UPDATE interview_definition
SET long_text_count = question_count;

ALTER TABLE interview_definition
    ALTER COLUMN long_text_count SET NOT NULL,
    ADD CONSTRAINT chk_question_composition_non_negative CHECK (
        mcq_single_count >= 0
        AND mcq_multiple_count >= 0
        AND short_text_count >= 0
        AND long_text_count >= 0
    ),
    ADD CONSTRAINT chk_question_composition_total CHECK (
        mcq_single_count + mcq_multiple_count + short_text_count + long_text_count = question_count
    );
