CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD_LETTER')),
    CONSTRAINT chk_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_outbox_completion CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR (status IN ('PENDING', 'DEAD_LETTER') AND published_at IS NULL)
    )
);

CREATE INDEX idx_outbox_publish
    ON outbox_event(status, next_attempt_at, created_at);
CREATE INDEX idx_outbox_aggregate
    ON outbox_event(aggregate_type, aggregate_id, created_at);
