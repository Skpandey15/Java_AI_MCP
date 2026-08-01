CREATE TABLE coaching_feedback (
    session_id UUID PRIMARY KEY REFERENCES interview_session(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'APPROVED')),
    leakage_safe BOOLEAN NOT NULL,
    leakage_flags TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE
);
