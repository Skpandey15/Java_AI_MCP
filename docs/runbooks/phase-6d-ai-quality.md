# Phase 6D AI quality and efficiency

## Release gate

`evaluation/release-dataset.jsonl` is a versioned, deterministic golden dataset.
Each record contains expected concepts, grounding evidence, forbidden output
patterns, a captured structured model response and its telemetry. CI runs:

```bash
uv run python -m evaluation.evaluator \
  evaluation/release-dataset.jsonl --output ai-quality-report.json
```

The build fails unless all schema, citation and safety cases pass, relevance is
at least 0.90, groundedness is at least 0.80, p95 latency is at most eight
seconds, and mean estimated cost is at most USD 0.02 per generation.

Captured responses must be reviewed and anonymized before adding them. Never
store candidate answers, credentials, access tokens or personal information in
the dataset. Add cases whenever a production defect or model/prompt change is
discovered. A model, routing or prompt-version change cannot be promoted if the
gate regresses.

## Generation quality regression

1. Compare the current CI artifact with the last accepted report.
2. Segment `ai_question_generations_total` by model policy and outcome.
3. Inspect only correlation IDs and safe metadata in logs; do not log prompts.
4. Roll back the model-policy or prompt-version change.
5. Add a redacted regression case before re-enabling promotion.

## Latency or cost budget

The AI service exports `/metrics` with generation count, latency histogram,
input/output token counters and configured-price cost estimates. Cost is an
estimate; provider invoices remain authoritative.

1. Check p95 latency, token direction and hourly estimated cost in Grafana.
2. Verify LiteLLM routing and provider health.
3. Reduce retrieved context or output size when token growth caused the breach.
4. Route to the approved fallback model only after its release dataset passes.
5. Update configured per-million-token prices when provider pricing changes.

## Safety response

Unknown citations, missing grounded citations and malformed structured output
are rejected before persistence. The release suite also blocks credential-like
output, instruction leakage and case-specific forbidden patterns. Treat any
real secret or personal-data disclosure as a security incident: disable the
affected model route, rotate exposed credentials, preserve safe audit metadata
and perform the standard incident process.
