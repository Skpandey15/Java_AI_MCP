# Phase 5B — MCP integration and tool governance

## Phase 5B.1 implemented

- Database-backed allow-listed MCP server and tool registry
- Four internal streamable-HTTP server definitions: interview, question bank,
  knowledge and result
- Narrow tool contracts with explicit read-only or state-changing access
- Strict JSON input/output schemas capped at 16 KiB
- Remote JSON Schema references rejected
- Disabled servers and tools excluded during resolution
- Interviewer-visible registry metadata with internal server URLs withheld
- Candidate access denied

The browser does not call MCP servers. Internal AI hosts resolve tools through
the registry and receive endpoints only inside the trusted service boundary.
Generic SQL, HTTP, filesystem, shell and code-execution tools are not registered.

## Phase 5B.2 implemented

- Exact workflow, tool and actor-role allow-list policies
- Maximum-call and authorization-TTL limits stored with each policy
- Candidate-safe enforcement independent of workflow configuration
- Mandatory approval policy for every state-changing tool
- HMAC-SHA256 signed authorization contexts bound to actor, role, workflow,
  server, tool and target resource
- Expiry and future-issued-token validation with constant-time signature checks
- Production/UAT secret injection through `MCP_AUTHORIZATION_SECRET`

## Phase 5B.3 implemented

- MCP 2025-11-25 initialize/initialized lifecycle over Streamable HTTP
- JSON-RPC request ID and protocol-version validation
- Optional `Mcp-Session-Id` lifecycle with authorization-context binding
- Both `application/json` and `text/event-stream` response support
- Signed authorization propagated on every request
- Strict tool input and structured-output validation
- 15-second read timeout, 3-second connect timeout and 1 MiB response cap
- Browser origins rejected with HTTP 403
- Exact authorized-tool discovery; the host never advertises unrelated tools
- Transport-neutral handler dispatcher ready for internal domain tools

The implementation targets the stable MCP protocol revision `2025-11-25`.

## Phase 5B.4 implemented

- Human approval queue for state-changing tools, restricted to the resource owner
- Approval decisions bound to the signed authorization context and expiry
- Required `Idempotency-Key` for state-changing calls
- Persisted successful-result replay and concurrent duplicate-call protection
- Atomic PostgreSQL per-context rolling-minute quotas
- The stricter of policy and deployment call limits is always applied
- Virtual-thread execution with configurable deadlines and interruption
- Persisted execution state for success, failure and timeout outcomes
- Approval, idempotency, quota and timeout failures returned as MCP errors

Runtime controls:

- `MCP_EXECUTION_TIMEOUT_SECONDS` — 1 to 60 seconds; default 10
- `MCP_CALLS_PER_MINUTE` — 1 to 100 calls; default 20

## Remaining Phase 5B increments

1. Result scanning, immutable tool-call auditing and telemetry
2. Internal server tool implementations and adversarial security tests
