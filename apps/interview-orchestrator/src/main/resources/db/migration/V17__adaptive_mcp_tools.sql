-- Register the three tools that complete each internal MCP server's designed capability
-- (blueprint, question-reuse, citation). They reuse the existing servers from V12.
INSERT INTO mcp_tool
    (id, server_id, tool_name, description, input_schema, output_schema,
     access_type, candidate_safe, enabled, created_at)
VALUES
    ('20000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000001',
     'get_skill_blueprint', 'Return the skill blueprint (skills, target difficulty, passing bar).',
     '{"type":"object","properties":{"interviewId":{"type":"string","format":"uuid"}},"required":["interviewId"],"additionalProperties":false}',
     '{"type":"object","properties":{"skills":{"type":"array","items":{"type":"object","properties":{"name":{"type":"string"},"targetDifficulty":{"type":"string"}},"required":["name","targetDifficulty"],"additionalProperties":false}},"targetDifficulty":{"type":"string"},"passingPercentage":{"type":"integer"},"questionCount":{"type":"integer"}},"required":["skills","targetDifficulty","passingPercentage","questionCount"],"additionalProperties":false}',
     'READ_ONLY', FALSE, TRUE, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000002',
     'check_question_reuse', 'Report whether a question was already asked in this session.',
     '{"type":"object","properties":{"sessionId":{"type":"string","format":"uuid"},"questionId":{"type":"string","format":"uuid"}},"required":["sessionId","questionId"],"additionalProperties":false}',
     '{"type":"object","properties":{"reused":{"type":"boolean"},"previouslyAskedAt":{"type":"string"}},"required":["reused"],"additionalProperties":false}',
     'READ_ONLY', FALSE, TRUE, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000003',
     'get_citation', 'Return a single knowledge citation (source chunk) by id.',
     '{"type":"object","properties":{"collectionId":{"type":"string","format":"uuid"},"chunkId":{"type":"string","format":"uuid"}},"required":["collectionId","chunkId"],"additionalProperties":false}',
     '{"type":"object","properties":{"chunkId":{"type":"string","format":"uuid"},"documentId":{"type":"string","format":"uuid"},"fileName":{"type":"string"},"chunkIndex":{"type":"integer"},"content":{"type":"string"}},"required":["chunkId","documentId","fileName","chunkIndex","content"],"additionalProperties":false}',
     'READ_ONLY', FALSE, TRUE, CURRENT_TIMESTAMP);

-- Read-only policies for the interviewer-driven question-generation workflow (mirrors the
-- existing sibling read tools). The adaptive-agent (SERVICE role) workflow is added in Phase 1.
INSERT INTO mcp_tool_policy
    (id, workflow, tool_id, actor_role, approval_required, max_calls,
     authorization_ttl_seconds, enabled, created_at)
VALUES
    ('30000000-0000-0000-0000-000000000005', 'QUESTION_GENERATION',
     '20000000-0000-0000-0000-000000000005', 'INTERVIEWER', FALSE, 5, 120, TRUE,
     CURRENT_TIMESTAMP),
    ('30000000-0000-0000-0000-000000000006', 'QUESTION_GENERATION',
     '20000000-0000-0000-0000-000000000006', 'INTERVIEWER', FALSE, 10, 120, TRUE,
     CURRENT_TIMESTAMP),
    ('30000000-0000-0000-0000-000000000007', 'QUESTION_GENERATION',
     '20000000-0000-0000-0000-000000000007', 'INTERVIEWER', FALSE, 10, 120, TRUE,
     CURRENT_TIMESTAMP);
