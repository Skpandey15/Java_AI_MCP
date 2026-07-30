CREATE TABLE mcp_tool_audit_event (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL,
    context_id UUID NOT NULL,
    actor_subject VARCHAR(255) NOT NULL,
    workflow VARCHAR(60) NOT NULL,
    server_key VARCHAR(80) NOT NULL,
    tool_name VARCHAR(120) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id UUID NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    detail VARCHAR(500),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_mcp_audit_event_type CHECK
        (event_type IN ('STARTED', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_mcp_audit_resource_time
    ON mcp_tool_audit_event(resource_type, resource_id, occurred_at);
CREATE INDEX idx_mcp_audit_actor_time
    ON mcp_tool_audit_event(actor_subject, occurred_at);

CREATE OR REPLACE FUNCTION prevent_mcp_audit_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'MCP audit events are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mcp_audit_no_update
BEFORE UPDATE OR DELETE ON mcp_tool_audit_event
FOR EACH ROW EXECUTE FUNCTION prevent_mcp_audit_mutation();

CREATE TABLE mcp_ai_evaluation (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES interview_session(id),
    context_id UUID NOT NULL UNIQUE,
    proposed_score INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_mcp_ai_score CHECK (proposed_score >= 0),
    CONSTRAINT chk_mcp_ai_status CHECK (status IN ('PENDING_REVIEW'))
);

CREATE INDEX idx_mcp_ai_evaluation_session
    ON mcp_ai_evaluation(session_id, created_at);
