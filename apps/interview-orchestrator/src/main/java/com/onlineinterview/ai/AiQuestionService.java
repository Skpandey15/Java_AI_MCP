package com.onlineinterview.ai;

import com.onlineinterview.interview.domain.InterviewStatus;
import com.onlineinterview.interview.domain.QuestionMode;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.session.domain.ManualQuestion;
import com.onlineinterview.session.infrastructure.ManualQuestionRepository;
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

    public AiQuestionService(InterviewDefinitionRepository definitions,
            ManualQuestionRepository questions, AiQuestionClient client) {
        this.definitions = definitions;
        this.questions = questions;
        this.client = client;
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
                || definition.getQuestionMode() != QuestionMode.DIRECT_LLM) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "AI questions require a DIRECT_LLM draft interview");
        }
        var response = client.generate(new AiQuestionClient.GenerationRequest(
                requestId, interviewId, definition.getSkills(),
                definition.getDifficulty().name(), definition.getQuestionCount()));
        var generated = response.questions().stream()
                .map(item -> ManualQuestion.generated(definition, item.order(), item.prompt(),
                        item.maxScore(), requestId, response.modelPolicy(), response.promptVersion()))
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
}
