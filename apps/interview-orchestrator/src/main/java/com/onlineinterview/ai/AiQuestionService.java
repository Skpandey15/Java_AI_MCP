package com.onlineinterview.ai;

import com.onlineinterview.interview.domain.InterviewStatus;
import com.onlineinterview.interview.domain.QuestionMode;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.knowledge.application.KnowledgeService;
import com.onlineinterview.knowledge.application.RagProperties;
import com.onlineinterview.knowledge.application.RagQualityMetrics;
import com.onlineinterview.knowledge.infrastructure.KnowledgeVectorStore;
import com.onlineinterview.session.domain.ManualQuestion;
import com.onlineinterview.session.domain.QuestionCitation;
import com.onlineinterview.session.infrastructure.ManualQuestionRepository;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiQuestionService {
    private static final Logger log = LoggerFactory.getLogger(AiQuestionService.class);
    private final InterviewDefinitionRepository definitions;
    private final ManualQuestionRepository questions;
    private final AiQuestionClient client;
    private final KnowledgeService knowledge;
    private final RagProperties ragProperties;
    private final RagQualityMetrics ragMetrics;
    private final CompositionJobStore jobs;
    // Self reference so @Async/@Transactional boundaries go through the Spring proxy.
    private final org.springframework.beans.factory.ObjectProvider<AiQuestionService> self;

    public AiQuestionService(InterviewDefinitionRepository definitions,
            ManualQuestionRepository questions, AiQuestionClient client,
            KnowledgeService knowledge, RagProperties ragProperties,
            RagQualityMetrics ragMetrics, CompositionJobStore jobs,
            org.springframework.beans.factory.ObjectProvider<AiQuestionService> self) {
        this.definitions = definitions;
        this.questions = questions;
        this.client = client;
        this.knowledge = knowledge;
        this.ragProperties = ragProperties;
        this.ragMetrics = ragMetrics;
        this.jobs = jobs;
        this.self = self;
    }

    /** Starts an async composition job and returns immediately with its status. Idempotent on
     *  the request id: a repeated call returns the existing job instead of starting another. */
    @Transactional
    public CompositionJobStore.Job startCompose(String ownerSubject, UUID interviewId,
            UUID requestId) {
        var existing = jobs.findByRequestId(requestId);
        if (existing.isPresent()) return existing.get();
        var definition = definitions.findById(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found"));
        if (!definition.getOwnerSubject().equals(ownerSubject)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found");
        }
        if (definition.getStatus() != InterviewStatus.DRAFT
                || (definition.getQuestionMode() != QuestionMode.DIRECT_LLM
                && definition.getQuestionMode() != QuestionMode.RAG)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Composition requires a DIRECT_LLM or RAG draft interview");
        }
        var jobId = UUID.randomUUID();
        jobs.create(jobId, interviewId, ownerSubject, requestId);
        self.getObject().runComposeAsync(jobId, ownerSubject, interviewId, requestId);
        return jobs.findByIdAndOwner(jobId, ownerSubject).orElseThrow();
    }

    /** Background worker: runs the compose loop and records the outcome on the job. Job-status
     *  writes auto-commit (JdbcTemplate) so FAILED is recorded even if the work tx rolls back. */
    @org.springframework.scheduling.annotation.Async
    public void runComposeAsync(UUID jobId, String ownerSubject, UUID interviewId, UUID requestId) {
        jobs.markRunning(jobId);
        try {
            var outcome = self.getObject().compose(ownerSubject, interviewId, requestId);
            jobs.markSucceeded(jobId, outcome.questions().size(), outcome.rounds());
        } catch (Exception exception) {
            log.atWarn().addKeyValue("event", "ai.composition_job_failed")
                    .addKeyValue("jobId", jobId).setCause(exception)
                    .log("Async composition job failed");
            jobs.markFailed(jobId, exception.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public CompositionJobStore.Job composeJob(String ownerSubject, UUID jobId) {
        return jobs.findByIdAndOwner(jobId, ownerSubject)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Composition job not found"));
    }

    @Transactional
    public List<ManualQuestion> generate(String ownerSubject, UUID interviewId, UUID requestId) {
        var existing = questions.findByGenerationRequestIdOrderByOrderAsc(requestId);
        if (!existing.isEmpty()) return existing;
        var definition = definitions.findById(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found"));
        if (!definition.getOwnerSubject().equals(ownerSubject)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found");
        }
        if (definition.getStatus() != InterviewStatus.DRAFT
                || (definition.getQuestionMode() != QuestionMode.DIRECT_LLM
                && definition.getQuestionMode() != QuestionMode.RAG)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "AI questions require a DIRECT_LLM or RAG draft interview");
        }
        var hits = retrieve(ownerSubject, definition);
        var response = client.generate(new AiQuestionClient.GenerationRequest(
                requestId, interviewId, definition.getSkills(),
                definition.getDifficulty().name(), definition.getQuestionCount(),
                definition.getQuestionComposition(), hits.stream()
                        .map(hit -> new AiQuestionClient.GroundingChunk(
                                hit.chunkId(), hit.fileName(), hit.content()))
                        .toList()));
        Map<UUID, KnowledgeVectorStore.SearchHit> authorized = hits.stream()
                .collect(Collectors.toMap(KnowledgeVectorStore.SearchHit::chunkId,
                        Function.identity()));
        var generated = response.questions().stream()
                .map(item -> toQuestion(definition, item, requestId, response, authorized))
                .toList();
        var saved = questions.saveAll(generated);
        if (definition.getQuestionMode() == QuestionMode.RAG) {
            var citations = saved.stream().flatMap(question -> question.getCitations().stream())
                    .toList();
            ragMetrics.generationCompleted(saved.size(), citations.size(),
                    citations.stream().map(QuestionCitation::getScore).toList());
        }
        log.atInfo().addKeyValue("event", "ai.questions_generated")
                .addKeyValue("interviewId", interviewId)
                .addKeyValue("generationRequestId", requestId)
                .addKeyValue("questionCount", saved.size())
                .addKeyValue("modelPolicy", response.modelPolicy())
                .log("AI questions generated");
        return saved;
    }

    /** Result of an agentic composition run: the saved questions plus the agent's own
     *  round count and per-question critique trace, so the UI can show its reasoning. */
    public record ComposeOutcome(List<ManualQuestion> questions, int rounds, List<String> trace) {}

    public List<String> suggestTopics(List<String> technologies, String difficulty) {
        if (technologies == null || technologies.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Select at least one technology");
        }
        return client.suggestTopics(technologies, difficulty == null ? "MEDIUM" : difficulty);
    }

    public AiQuestionClient.TopicDetailsResponse topicDetails(
            String ecosystem, String technology, String topic, String variant) {
        if (ecosystem == null || ecosystem.isBlank() || technology == null || technology.isBlank()
                || topic == null || topic.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ecosystem, technology and topic are required");
        }
        String v = variant == null ? "" : variant.strip().toLowerCase();
        String resolved = (v.equals("notes") || v.equals("design")) ? v : "guide";
        return client.topicDetails(ecosystem.strip(), technology.strip(), topic.strip(), resolved);
    }

    @Transactional
    public ComposeOutcome compose(String ownerSubject, UUID interviewId, UUID requestId) {
        var existing = questions.findByGenerationRequestIdOrderByOrderAsc(requestId);
        if (!existing.isEmpty()) return new ComposeOutcome(existing, 0, List.of());
        var definition = definitions.findById(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found"));
        if (!definition.getOwnerSubject().equals(ownerSubject)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found");
        }
        if (definition.getStatus() != InterviewStatus.DRAFT
                || (definition.getQuestionMode() != QuestionMode.DIRECT_LLM
                && definition.getQuestionMode() != QuestionMode.RAG)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Composition requires a DIRECT_LLM or RAG draft interview");
        }
        var current = questions.findByInterviewDefinitionIdOrderByOrderAsc(interviewId);
        var existingPrompts = current.stream().map(ManualQuestion::getPrompt).toList();
        List<String> grounding = List.of();
        if (definition.getQuestionMode() == QuestionMode.RAG) {
            grounding = retrieve(ownerSubject, definition).stream()
                    .map(KnowledgeVectorStore.SearchHit::content).toList();
        }
        var response = client.compose(new AiQuestionClient.ComposeRequest(
                definition.getSkills(), definition.getDifficulty().name(),
                definition.getQuestionCount(), definition.getQuestionComposition(),
                existingPrompts, grounding, 3));
        var generated = new java.util.ArrayList<ManualQuestion>();
        int order = current.size();
        for (var q : response.questions()) {
            order++;
            var type = q.type() == null
                    ? com.onlineinterview.session.domain.QuestionType.LONG_TEXT : q.type();
            var options = q.options() == null ? List.<String>of() : q.options();
            var correct = q.correctAnswers() == null ? List.<String>of() : q.correctAnswers();
            generated.add(ManualQuestion.generated(definition, order, q.prompt(), 10,
                    type, options, correct, requestId, "composition-agent", "compose-v1"));
        }
        var saved = questions.saveAll(generated);
        log.atInfo().addKeyValue("event", "ai.interview_composed")
                .addKeyValue("interviewId", interviewId)
                .addKeyValue("generationRequestId", requestId)
                .addKeyValue("rounds", response.rounds())
                .addKeyValue("questionCount", saved.size())
                .log("Interview composed by agent");
        return new ComposeOutcome(saved, response.rounds(), response.trace());
    }

    private List<KnowledgeVectorStore.SearchHit> retrieve(
            String ownerSubject, com.onlineinterview.interview.domain.InterviewDefinition definition) {
        if (definition.getQuestionMode() != QuestionMode.RAG) return List.of();
        var query = definition.getTitle() + " " + String.join(" ", definition.getSkills());
        var sample = ragMetrics.startRetrieval();
        var hits = knowledge.search(ownerSubject, definition.getKnowledgeCollectionId(), query,
                ragProperties.getRetrievalLimit(), ragProperties.getMinimumSimilarity());
        ragMetrics.retrievalCompleted(sample,
                hits.stream().map(KnowledgeVectorStore.SearchHit::score).toList());
        if (hits.isEmpty()) {
            ragMetrics.generationRejected("no_relevant_context");
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Knowledge collection has no content above the similarity threshold");
        }
        double averageScore = hits.stream().mapToDouble(KnowledgeVectorStore.SearchHit::score)
                .average().orElse(0);
        log.atInfo().addKeyValue("event", "rag.retrieval_completed")
                .addKeyValue("interviewId", definition.getId())
                .addKeyValue("hitCount", hits.size())
                .addKeyValue("minimumSimilarity", ragProperties.getMinimumSimilarity())
                .addKeyValue("averageSimilarity", averageScore)
                .log("RAG retrieval completed");
        return hits;
    }

    private ManualQuestion toQuestion(
            com.onlineinterview.interview.domain.InterviewDefinition definition,
            AiQuestionClient.GeneratedQuestion item, UUID requestId,
            AiQuestionClient.GenerationResponse response,
            Map<UUID, KnowledgeVectorStore.SearchHit> authorized) {
        var citationIds = item.citationIds() == null ? List.<UUID>of() : item.citationIds();
        if (definition.getQuestionMode() == QuestionMode.DIRECT_LLM) {
            if (!citationIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Direct generation returned unexpected citations");
            }
            return ManualQuestion.generated(definition, item.order(), item.prompt(),
                    item.maxScore(), item.type(), item.options(), item.correctAnswers(),
                    requestId, response.modelPolicy(), response.promptVersion());
        }
        var citations = citationIds.stream().distinct().map(id -> {
            var hit = authorized.get(id);
            if (hit == null) {
                ragMetrics.generationRejected("unauthorized_citation");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "AI service returned an unauthorized citation");
            }
            return new QuestionCitation(hit.chunkId(), hit.documentId(), hit.fileName(),
                    hit.chunkIndex(), hit.content(), hit.score());
        }).toList();
        if (citations.isEmpty()) {
            ragMetrics.generationRejected("missing_citation");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "RAG generation returned a question without citations");
        }
        return ManualQuestion.generatedRag(definition, item.order(), item.prompt(),
                item.maxScore(), item.type(), item.options(), item.correctAnswers(),
                requestId, response.modelPolicy(), response.promptVersion(), citations);
    }
}
