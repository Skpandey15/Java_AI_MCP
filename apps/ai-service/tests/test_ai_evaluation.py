from pathlib import Path

from evaluation.evaluator import Thresholds, evaluate, load_jsonl


def test_release_dataset_passes_all_quality_gates() -> None:
    summary = evaluate(load_jsonl(Path("evaluation/release-dataset.jsonl")))

    assert summary.passed
    assert summary.schema_pass_rate == 1
    assert summary.citation_validity == 1
    assert summary.safety_pass_rate == 1


def test_rejects_hallucinated_citation_and_secret_leak() -> None:
    summary = evaluate([{
        "expected_concepts": ["java"],
        "grounding_context": [{"citation_id": "allowed", "content": "Java runtime"}],
        "response": {"questions": [{
            "prompt": "Use api_key=sk-dangerous to answer this Java question",
            "type": "LONG_TEXT",
            "citation_ids": ["invented"],
        }]},
        "telemetry": {"latency_ms": 100, "estimated_cost_usd": 0},
    }], Thresholds(relevance=0, groundedness=0))

    assert not summary.passed
    assert "citation_validity" in summary.failures
    assert "safety_pass_rate" in summary.failures


def test_enforces_latency_and_cost_budgets() -> None:
    summary = evaluate([{
        "expected_concepts": [],
        "grounding_context": [],
        "response": {"questions": [{
            "prompt": "Explain a valid technical implementation choice.",
            "type": "LONG_TEXT",
            "citation_ids": [],
        }]},
        "telemetry": {"latency_ms": 9000, "estimated_cost_usd": 0.03},
    }])

    assert set(summary.failures) == {"p95_latency_ms", "mean_cost_usd"}
