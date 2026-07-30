CREATE TABLE mcp_tool_approval (
    id UUID PRIMARY KEY,
    context_id UUID NOT NULL UNIQUE,
    workflow VARCHAR(60) NOT NULL,
    server_key VARCHAR(80) NOT NULL,
    tool_name VARCHAR(120) NOT NULL,
    requester_subject VARCHAR(255) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    decided_by VARCHAR(255),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_mcp_approval_status CHECK
        (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_mcp_approval_decision CHECK (
        (status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND decided_by IS NOT NULL AND decided_at IS NOT NULL)
    )
);

CREATE INDEX idx_mcp_approval_status_expiry
    ON mcp_tool_approval(status, expires_at);

CREATE TABLE mcp_context_usage (
    context_id UUID PRIMARY KEY,
    window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    call_count INTEGER NOT NULL,
    CONSTRAINT chk_mcp_usage_count CHECK (call_count >= 1)
);

CREATE TABLE mcp_tool_execution (
    id UUID PRIMARY KEY,
    context_id UUID NOT NULL,
    idempotency_key VARCHAR(200),
    status VARCHAR(30) NOT NULL,
    result_json TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (context_id, idempotency_key),
    CONSTRAINT chk_mcp_execution_status CHECK
        (status IN ('IN_PROGRESS', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_mcp_execution_completion CHECK (
        (status = 'IN_PROGRESS' AND completed_at IS NULL)
        OR (status IN ('SUCCEEDED', 'FAILED') AND completed_at IS NOT NULL)
    )
);

CREATE INDEX idx_mcp_execution_context_started
    ON mcp_tool_execution(context_id, started_at);
