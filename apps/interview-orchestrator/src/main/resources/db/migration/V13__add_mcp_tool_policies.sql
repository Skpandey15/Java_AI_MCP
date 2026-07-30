CREATE TABLE mcp_tool_policy (
    id UUID PRIMARY KEY,
    workflow VARCHAR(60) NOT NULL,
    tool_id UUID NOT NULL REFERENCES mcp_tool(id) ON DELETE CASCADE,
    actor_role VARCHAR(40) NOT NULL,
    approval_required BOOLEAN NOT NULL,
    max_calls INTEGER NOT NULL,
    authorization_ttl_seconds INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (workflow, tool_id, actor_role),
    CONSTRAINT chk_mcp_policy_workflow CHECK
        (workflow IN ('QUESTION_GENERATION', 'ANSWER_EVALUATION')),
    CONSTRAINT chk_mcp_policy_actor_role CHECK
        (actor_role IN ('INTERVIEWER', 'CANDIDATE', 'SERVICE')),
    CONSTRAINT chk_mcp_policy_max_calls CHECK (max_calls BETWEEN 1 AND 100),
    CONSTRAINT chk_mcp_policy_ttl CHECK (authorization_ttl_seconds BETWEEN 30 AND 900)
);

CREATE INDEX idx_mcp_policy_lookup
    ON mcp_tool_policy(workflow, tool_id, actor_role, enabled);

INSERT INTO mcp_tool_policy
    (id, workflow, tool_id, actor_role, approval_required, max_calls,
     authorization_ttl_seconds, enabled, created_at)
VALUES
    ('30000000-0000-0000-0000-000000000001', 'QUESTION_GENERATION',
     '20000000-0000-0000-0000-000000000001', 'INTERVIEWER', FALSE, 5, 120, TRUE,
     CURRENT_TIMESTAMP),
    ('30000000-0000-0000-0000-000000000002', 'QUESTION_GENERATION',
     '20000000-0000-0000-0000-000000000002', 'INTERVIEWER', FALSE, 5, 120, TRUE,
     CURRENT_TIMESTAMP),
    ('30000000-0000-0000-0000-000000000003', 'QUESTION_GENERATION',
     '20000000-0000-0000-0000-000000000003', 'INTERVIEWER', FALSE, 10, 120, TRUE,
     CURRENT_TIMESTAMP),
    ('30000000-0000-0000-0000-000000000004', 'ANSWER_EVALUATION',
     '20000000-0000-0000-0000-000000000004', 'SERVICE', TRUE, 1, 60, TRUE,
     CURRENT_TIMESTAMP);
