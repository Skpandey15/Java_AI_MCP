# Implementation Plan: Adaptive AI Interviewer

Companion to [adaptive-ai-interviewer.md](adaptive-ai-interviewer.md) (HLD/LLD). This is the
**build plan**: what ships in each phase, the exact files, acceptance criteria, and tests.

## Overview

The work is divided into **4 phases (Phase 0 → Phase 3)**, each an independently mergeable PR,
built in dependency order behind the `ADAPTIVE_ENABLED` flag (default `false`) so nothing is
user-visible until Phase 3.

| Phase | Deliverable | Depends on | Independently useful? | Required checks |
|---|---|---|---|---|
| **0** | 3 new MCP tools (blueprint, reuse, citation) | — | ✅ yes (tools usable alone) | 95% coverage on `mcp/**` |
| **1** | Agent turn engine (ai-service + orchestrator, flag-off) | 0 | ⚠️ internal only | coverage `session/**`, ai-service ≥95%, eval gate |
| **2** | Candidate + interviewer adaptive UX | 1 | ⚠️ behind flag | web-ui tests |
| **3** | Dark-launch → GA (flag on, dashboards, pilot) | 2 | ✅ user-facing | — |

**Sequencing rule:** each phase's PR must be green and merged before the next starts (Phases build on
each other's classes/migrations). Migrations are strictly additive and ordered (`V17`, `V18`).

---

## Phase 0 — The 3 MCP tools

Closes the "missing second tool per server" gap; gives the agent its hands. No agent yet.

**New handlers** (`apps/interview-orchestrator/.../mcp/transport/`, implement `McpToolHandler`):
- [ ] `SkillBlueprintToolHandler` — `interview` / `get_skill_blueprint` → `{skills:[{name,weight,targetDifficulty,rubric}]}`
- [ ] `QuestionReuseToolHandler` — `question-bank` / `check_question_reuse` → `{reused, previouslyAskedAt?}`
- [ ] `CitationToolHandler` — `knowledge` / `get_citation` → `{documentId,fileName,chunkIndex,content,sourceUri?}`

**Supporting changes:**
- [ ] `V17__adaptive_mcp_tools.sql` — register the 3 tools in the MCP registry + default `McpToolPolicy` rows (mirror `V12`–`V15`)
- [ ] Input JSON-schemas wired through `McpSchemaValidator`
- [ ] Reuse-lookup query in `question-bank` domain (has a prompt/question been asked in this session?)
- [ ] Unit tests for each handler (happy path, authz failure, bad args) — **`mcp/**` is coverage-gated at 95%**

**Acceptance:** each tool callable via `POST /internal/mcp/{serverKey}` `tools/call`; unauthorized/tenant-mismatch rejected; audit event written; coverage gate green. **PR: "feat(mcp): add skill-blueprint, question-reuse, citation tools".**

---

## Phase 1 — Agent turn engine (flag-off)

The durable outer loop + the agentic inner loop. Wired end-to-end but `ADAPTIVE_ENABLED=false`.

**ai-service (`apps/ai-service/app/`):**
- [ ] `llm/chat_client.py` → add `complete_with_tools(system, messages, tools, schema, max_tool_calls)` (OpenAI tool-call round-trip via LiteLLM; aggregates usage)
- [ ] `mcp/tool_client.py` → JSON-RPC client to `/internal/mcp/{serverKey}` (`initialize` once, then `tools/call`), scoped service token + trace id
- [ ] `application/interviewer_agent.py` → `InterviewerAgent.next_turn(req)`: ReAct loop + Python-enforced invariants (terminate/coverage/reuse/grounding/fallback)
- [ ] `api/interview_routes.py` → `POST /internal/v1/interview:next-turn`
- [ ] `domain/interview_turn_models.py` → `NextTurnRequest` / `NextTurnResponse` (schemas in LLD §3.3)

**orchestrator (`.../session/adaptive/`):**
- [ ] `AdaptiveSessionState`, `AdaptiveTurn` JPA entities + repositories
- [ ] `V18__adaptive_session_state.sql` — the two tables (ER in LLD §3.1); add `ADAPTIVE` to `question_mode`
- [ ] `AdaptiveInterviewClient` — `RestClient` → ai-service `next-turn` (mirror `AiAssessmentClient`: `X-Service-Token`, `DownstreamCallExecutor`)
- [ ] `AdaptiveSessionService` — durable turn loop: persist answer → build request → call agent → apply (persist turn / conclude via approval-gated `submit_ai_evaluation`); enforce budgets
- [ ] `AdaptiveSessionController` — start / answer / resume (candidate-scoped), **guarded by `ADAPTIVE_ENABLED`**
- [ ] `AdaptiveProperties` (`@ConfigurationProperties("app.adaptive")`) — flag, maxTurns, maxToolCalls, tokenBudget, modelPolicy

**CI eval gate:**
- [ ] Extend `apps/ai-service/evaluation/` with an **agent-trajectory dataset** (fixed transcripts → expected next-move properties: covers-a-skill / no-reuse / concludes-on-budget), run by the existing `evaluation.evaluator` release gate

**Tests:** `AdaptiveSessionService` invariants (budget/coverage/terminate) — **`session/**` is coverage-gated**; `InterviewerAgent` with stubbed `ChatClient`/`ToolClient` (invariants hold regardless of model output); ai-service overall ≥95%.

**Acceptance:** with the flag on in a test profile, a scripted answer sequence drives ask→ask→conclude with no reuse, grounded generated questions, and a proposed evaluation queued for approval. **PR: "feat(agent): adaptive interviewer turn engine (flagged off)".**

---

## Phase 2 — Adaptive UX

- [ ] `web-ui`: `interviewApi` methods (`startAdaptiveSession`, `answerAdaptive`, `getAdaptiveSession`)
- [ ] Candidate `AdaptiveSessionPage.tsx` — one adaptive question at a time, progress, submit-answer→next
- [ ] Interviewer transcript + rationale view on the submission/review page; approve the proposed evaluation (reuses the MCP approval UI)
- [ ] `RAG`/`ADAPTIVE` mode selector in the create-interview form
- [ ] vitest for the adaptive flow (mock the API)

**Acceptance:** behind the flag, a candidate completes an adaptive interview and the interviewer sees the transcript + approves the result. **PR: "feat(ui): adaptive interview candidate + reviewer views".**

---

## Phase 3 — Dark-launch → GA

- [ ] Grafana panels: turns/interview, tool-calls/interview, tokens+cost/interview, fallback rate
- [ ] Enable `ADAPTIVE_ENABLED=true` for a **single pilot** interview definition (env in the dev/uat overlay)
- [ ] Fairness review of adaptive scoring vs fixed-mode (comparable, explainable via blueprint+audit)
- [ ] Runbook section: operating/monitoring adaptive interviews; kill-switch = flag off
- [ ] GA: enable broadly after pilot metrics are within budget

**Acceptance:** pilot interviews run within cost/latency budget; fairness review signed off; flip to GA.

---

## Cross-cutting

- **Feature flag:** `ADAPTIVE_ENABLED` gates the controller + mode; **kill-switch** = set it `false` (no redeploy of logic needed).
- **Coverage gates:** new `mcp/**` and `session/**` code is in the 95% line-coverage set — budget test time accordingly. ai-service keeps `--cov-fail-under=95`.
- **Security:** candidate answers are untrusted (delimited in-prompt + `McpResultScanner`); only `submit_ai_evaluation` mutates state and stays approval-gated; every tool call audited.
- **Rollback:** each phase is a revertible PR; runtime rollback is the flag. Migrations are additive (no destructive down-migrations).
- **PR-per-phase**, each passing the `ci-required` ruleset (the 6 checks) before merge → auto-deploy to dev via the consolidated `pipeline.yml`.

## Summary

**4 phases:** (0) MCP tools → (1) agent turn engine → (2) UX → (3) dark-launch/GA. Phases 0–2 ship
dark behind `ADAPTIVE_ENABLED`; Phase 3 turns it on. Recommended first step: **Phase 0**, the
self-contained MCP tools.
