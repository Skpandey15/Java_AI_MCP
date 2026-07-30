package com.onlineinterview.ai;

import com.onlineinterview.interview.domain.InterviewStatus;
import com.onlineinterview.interview.domain.QuestionMode;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.knowledge.application.KnowledgeService;
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

    public AiQuestionService(InterviewDefinitionRepository definitions,
            ManualQuestionRepository questions, AiQuestionClient client,
            KnowledgeService knowledge) {
        this.definitions = definitions;
        this.questions = questions;
        this.client = client;
        this.knowledge = knowledge;
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
        log.atInfo().addKeyValue("event", "ai.questions_generated")
                .addKeyValue("interviewId", interviewId)
                .addKeyValue("generationRequestId", requestId)
                .addKeyValue("questionCount", saved.size())
                .addKeyValue("modelPolicy", response.modelPolicy())
                .log("AI questions generated");
        return saved;
    }

    private List<KnowledgeVectorStore.SearchHit> retrieve(
            String ownerSubject, com.onlineinterview.interview.domain.InterviewDefinition definition) {
        if (definition.getQuestionMode() != QuestionMode.RAG) return List.of();
        var query = definition.getTitle() + " " + String.join(" ", definition.getSkills());
        var hits = knowledge.search(ownerSubject, definition.getKnowledgeCollectionId(), query, 8);
        if (hits.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Knowledge collection has no searchable content");
        }
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
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "AI service returned an unauthorized citation");
            }
            return new QuestionCitation(hit.chunkId(), hit.documentId(), hit.fileName(),
                    hit.chunkIndex(), hit.content(), hit.score());
        }).toList();
        if (citations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "RAG generation returned a question without citations");
        }
        return ManualQuestion.generatedRag(definition, item.order(), item.prompt(),
                item.maxScore(), item.type(), item.options(), item.correctAnswers(),
                requestId, response.modelPolicy(), response.promptVersion(), citations);
    }
}
