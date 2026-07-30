from prometheus_client import Counter, Histogram

GENERATIONS = Counter(
    "ai_question_generations_total",
    "Question-generation attempts by outcome and model policy.",
    ["outcome", "model_policy"],
)
LATENCY = Histogram(
    "ai_question_generation_latency_seconds",
    "Model-gateway latency for question generation.",
    buckets=(0.25, 0.5, 1, 2, 4, 8, 16, 32, 64),
)
TOKENS = Counter(
    "ai_question_generation_tokens_total",
    "Model tokens consumed by question generation.",
    ["direction", "model_policy"],
)
COST = Counter(
    "ai_question_generation_estimated_cost_usd_total",
    "Configured-price estimate for question generation.",
    ["model_policy"],
)


def record_success(model: str, prompt_tokens: int, completion_tokens: int,
                   estimated_cost_usd: float, latency_ms: int) -> None:
    GENERATIONS.labels("success", model).inc()
    LATENCY.observe(latency_ms / 1000)
    TOKENS.labels("input", model).inc(prompt_tokens)
    TOKENS.labels("output", model).inc(completion_tokens)
    COST.labels(model).inc(estimated_cost_usd)


def record_failure() -> None:
    GENERATIONS.labels("failure", "unknown").inc()
