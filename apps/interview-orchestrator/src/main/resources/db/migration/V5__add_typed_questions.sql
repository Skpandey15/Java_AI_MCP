ALTER TABLE manual_question
    ADD COLUMN question_type VARCHAR(30) NOT NULL DEFAULT 'LONG_TEXT';

ALTER TABLE manual_question
    ADD CONSTRAINT chk_question_type
    CHECK (question_type IN ('MCQ_SINGLE', 'MCQ_MULTIPLE', 'SHORT_TEXT', 'LONG_TEXT'));

CREATE TABLE question_option (
    question_id UUID NOT NULL REFERENCES manual_question(id) ON DELETE CASCADE,
    option_order INTEGER NOT NULL,
    option_value VARCHAR(1000) NOT NULL,
    PRIMARY KEY (question_id, option_order)
);

CREATE TABLE question_correct_answer (
    question_id UUID NOT NULL REFERENCES manual_question(id) ON DELETE CASCADE,
    answer_order INTEGER NOT NULL,
    answer_value VARCHAR(1000) NOT NULL,
    PRIMARY KEY (question_id, answer_order)
);
