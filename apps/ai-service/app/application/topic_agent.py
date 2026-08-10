"""Topic-suggestion agent: given the chosen technologies, propose distinct, interview-relevant
sub-topics the interviewer can pick from to focus question generation."""

from app.domain.question_models import GenerationUsage
from app.domain.topic_models import (
    SuggestTopicsRequest,
    SuggestTopicsResponse,
    TopicDetailsRequest,
    TopicDetailsResponse,
)
from app.llm.chat_client import ChatClient

_SCHEMA = {
    "type": "object",
    "properties": {"topics": {"type": "array", "items": {"type": "string"}}},
    "required": ["topics"],
    "additionalProperties": False,
}

_DETAIL_SCHEMA = {
    "type": "object",
    "properties": {"title": {"type": "string"}, "content": {"type": "string"}},
    "required": ["title", "content"],
    "additionalProperties": False,
}


class TopicAgent:
    def __init__(self, client: ChatClient | None = None) -> None:
        self.client = client or ChatClient()

    def suggest(self, request: SuggestTopicsRequest) -> SuggestTopicsResponse:
        technologies = ", ".join(request.technologies)
        system = (
            "You are a technical interview planner. Given one or more technologies, list the "
            "distinct, interview-relevant sub-topics an interviewer might focus questions on. "
            "Use concise topic names (2-4 words), specific and non-overlapping, ordered by "
            "importance, and cover every technology provided. Do not repeat the technology names "
            "themselves as topics."
        )
        user = (
            f"Technologies: {technologies}\n"
            f"Target difficulty: {request.difficulty}\n"
            f"Return up to {request.max_topics} topics."
        )
        parsed, usage = self.client.complete_json(system, user, _SCHEMA)
        seen: set[str] = set()
        topics: list[str] = []
        for item in parsed.get("topics", []):
            name = str(item).strip()
            key = name.lower()
            if name and key not in seen:
                seen.add(key)
                topics.append(name)
        return SuggestTopicsResponse(
            topics=topics[: request.max_topics],
            usage=GenerationUsage(
                prompt_tokens=usage["prompt_tokens"],
                completion_tokens=usage["completion_tokens"],
                total_tokens=usage["total_tokens"],
                estimated_cost_usd=usage["estimated_cost_usd"],
                latency_ms=usage["latency_ms"],
            ),
        )

    def details(self, request: TopicDetailsRequest) -> TopicDetailsResponse:
        if request.variant == "notes":
            system = (
                "You are a senior interviewer and mentor. Write concise, high-yield INTERVIEW "
                "PREP NOTES in Markdown for the given topic — optimized for someone revising just "
                "before a technical interview, not a full tutorial. Use ## sections in this "
                "order: 'Key concepts to know cold' (5-8 tight bullets); 'Most-asked interview "
                "questions' (each a bold question followed by a 2-4 sentence model answer); "
                "'Follow-ups & gotchas' interviewers probe; 'Common mistakes / red flags to "
                "avoid'; 'Snippets to remember' (only where a short code/command example truly "
                "helps, label its language); and 'If you remember nothing else' — a 30-second "
                "summary. Be accurate and specific, prefer bullets over prose, keep it scannable, "
                "and stay focused on what actually comes up in interviews. Never claim it is "
                "exhaustive."
            )
            user = (f"Ecosystem: {request.ecosystem}\nTechnology: {request.technology}\n"
                    f"Topic: {request.topic}\nWrite the interview prep notes.")
        elif request.variant == "release":
            system = (
                "You are a senior engineer writing a RELEASE / WHAT'S-NEW summary in Markdown for "
                "a specific product version (the topic names the version, e.g. 'Java 21' or "
                "'Spring Boot 3.2'). Focus on what changed in THAT version, not a tutorial. Use "
                "## sections in this order: 'Headline features' (the marquee additions, with a "
                "one-line why-it-matters each); 'Language / API additions' (new syntax, APIs, or "
                "capabilities, with tiny labelled code snippets where helpful); 'Enhancements & "
                "performance'; 'Deprecations & removals'; 'Migration notes' (what to change when "
                "upgrading to this version); and 'Interview angle' (what an interviewer might ask "
                "about this release). Mark preview/incubator features as such, be accurate and "
                "version-specific, prefer bullets, and never invent features. If unsure whether a "
                "feature landed in this exact version, say so rather than guess."
            )
            user = (f"Ecosystem: {request.ecosystem}\nTechnology: {request.technology}\n"
                    f"Topic (version): {request.topic}\n"
                    f"Summarize what is new in this version.")
        elif request.variant == "design":
            system = (
                "You are a principal software architect. Explain the given topic through a "
                "SOFTWARE DESIGN and ARCHITECTURE lens in Markdown — where it fits in a system "
                "and how it shapes design decisions, not a how-to tutorial. Use ## sections in "
                "this order: 'Where it fits' (which layer or part of a system it belongs to and "
                "the problem it solves) — and inside this section include an architecture "
                "diagram as a Mermaid flowchart in a ```mermaid fenced block (use `graph TD` or "
                "`graph LR`, 6-12 nodes with short labels, showing this topic's component and how "
                "requests or data flow through the neighbouring components); "
                "'Design decisions & trade-offs' it drives; 'When to use "
                "it vs alternatives' (and when NOT to); 'How it interacts with other components' "
                "(patterns, integration points, data flow); 'Architectural pitfalls & "
                "anti-patterns'; 'Design-interview angle' (how to reason about designing a system "
                "with it and what an interviewer probes); and 'Fits in the big picture' — a "
                "concrete example architecture that uses it. Be concrete, cite well-known design "
                "patterns and real-world scenarios, prefer bullets, and label any code or "
                "diagram-as-text fences. Never claim it is exhaustive."
            )
            user = (f"Ecosystem: {request.ecosystem}\nTechnology: {request.technology}\n"
                    f"Topic: {request.topic}\nExplain where this fits from a design perspective.")
        else:
            system = (
                "You are a senior technical educator. Write a self-contained, accurate "
                "zero-to-hero learning guide in Markdown. Cover prerequisites, fundamentals, "
                "mental models, setup, syntax or APIs, progressively advanced concepts, "
                "production patterns, security, performance, testing, debugging, common "
                "mistakes, a practical project, interview questions with concise answers, "
                "exercises, and a mastery checklist. Use concrete examples and code fences "
                "where relevant. Make it easy to scan: begin with a short overview and learning "
                "outcomes, use ## sections and ### subsections, keep paragraphs under four "
                "sentences, use bullets for steps and checklists, tables only for genuine "
                "comparisons, and label every code fence with its language. Present concepts in "
                "beginner, intermediate, advanced, and production order. Never claim the guide "
                "is literally exhaustive."
            )
            user = (f"Ecosystem: {request.ecosystem}\nTechnology: {request.technology}\n"
                    f"Topic: {request.topic}\nCreate the complete guided learning path.")
        parsed, usage = self.client.complete_json(system, user, _DETAIL_SCHEMA)
        return TopicDetailsResponse(
            title=str(parsed.get("title", request.topic)).strip() or request.topic,
            content=str(parsed.get("content", "")).strip(),
            usage=GenerationUsage(**usage),
        )
