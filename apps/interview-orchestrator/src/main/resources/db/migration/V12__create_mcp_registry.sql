CREATE TABLE mcp_server (
    id UUID PRIMARY KEY,
    server_key VARCHAR(80) NOT NULL UNIQUE,
    display_name VARCHAR(160) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    transport VARCHAR(40) NOT NULL,
    classification VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_mcp_transport CHECK (transport IN ('STREAMABLE_HTTP')),
    CONSTRAINT chk_mcp_classification CHECK (classification IN ('INTERNAL', 'EXTERNAL_APPROVED'))
);

CREATE TABLE mcp_tool (
    id UUID PRIMARY KEY,
    server_id UUID NOT NULL REFERENCES mcp_server(id) ON DELETE CASCADE,
    tool_name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL,
    input_schema TEXT NOT NULL,
    output_schema TEXT NOT NULL,
    access_type VARCHAR(40) NOT NULL,
    candidate_safe BOOLEAN NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (server_id, tool_name),
    CONSTRAINT chk_mcp_access_type CHECK (access_type IN ('READ_ONLY', 'STATE_CHANGING'))
);

CREATE INDEX idx_mcp_tool_server_enabled ON mcp_tool(server_id, enabled);

INSERT INTO mcp_server
    (id, server_key, display_name, base_url, transport, classification, enabled, created_at)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'interview', 'Interview MCP',
     'http://interview-orchestrator:8080/internal/mcp/interview',
     'STREAMABLE_HTTP', 'INTERNAL', TRUE, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000002', 'question-bank', 'Question Bank MCP',
     'http://interview-orchestrator:8080/internal/mcp/question-bank',
     'STREAMABLE_HTTP', 'INTERNAL', TRUE, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000003', 'knowledge', 'Knowledge MCP',
     'http://ai-service:8000/internal/mcp/knowledge',
     'STREAMABLE_HTTP', 'INTERNAL', TRUE, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000004', 'result', 'Result MCP',
     'http://interview-orchestrator:8080/internal/mcp/result',
     'STREAMABLE_HTTP', 'INTERNAL', TRUE, CURRENT_TIMESTAMP);

INSERT INTO mcp_tool
    (id, server_id, tool_name, description, input_schema, output_schema,
     access_type, candidate_safe, enabled, created_at)
VALUES
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'get_interview_context', 'Return minimum authorized interview context.',
     '{"type":"object","properties":{"interviewId":{"type":"string","format":"uuid"}},"required":["interviewId"],"additionalProperties":false}',
     '{"type":"object","properties":{"title":{"type":"string"},"skills":{"type":"array","items":{"type":"string"}}},"required":["title","skills"],"additionalProperties":false}',
     'READ_ONLY', FALSE, TRUE, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002',
     'search_approved_questions', 'Search approved reusable questions by skill.',
     '{"type":"object","properties":{"skill":{"type":"string"},"limit":{"type":"integer"}},"required":["skill"],"additionalProperties":false}',
     '{"type":"object","properties":{"questionIds":{"type":"array","items":{"type":"string","format":"uuid"}}},"required":["questionIds"],"additionalProperties":false}',
     'READ_ONLY', FALSE, TRUE, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003',
     'search_knowledge', 'Search an authorized knowledge collection.',
     '{"type":"object","properties":{"collectionId":{"type":"string","format":"uuid"},"query":{"type":"string"}},"required":["collectionId","query"],"additionalProperties":false}',
     '{"type":"object","properties":{"citations":{"type":"array","items":{"type":"object"}}},"required":["citations"],"additionalProperties":false}',
     'READ_ONLY', FALSE, TRUE, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000004',
     'submit_ai_evaluation', 'Submit an authorized AI evaluation for review.',
     '{"type":"object","properties":{"sessionId":{"type":"string","format":"uuid"},"score":{"type":"integer"}},"required":["sessionId","score"],"additionalProperties":false}',
     '{"type":"object","properties":{"accepted":{"type":"boolean"}},"required":["accepted"],"additionalProperties":false}',
     'STATE_CHANGING', FALSE, TRUE, CURRENT_TIMESTAMP);
